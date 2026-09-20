package team4.emotionmap.letter;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import team4.emotionmap.account.AccountAccessService;
import team4.emotionmap.account.User;
import team4.emotionmap.account.UserRepository;
import team4.emotionmap.contracts.config.PaginationProperties;
import team4.emotionmap.contracts.config.ServiceConfigSource;
import team4.emotionmap.contracts.config.ServiceLimits;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.error.FieldError;
import team4.emotionmap.contracts.media.ImageFileLifecycle;
import team4.emotionmap.contracts.media.LocalImageStore;
import team4.emotionmap.contracts.memory.DistributionType;
import team4.emotionmap.contracts.memory.MemorySnapshot;
import team4.emotionmap.contracts.memory.MemorySnapshotReader;
import team4.emotionmap.contracts.page.PageInfo;
import team4.emotionmap.contracts.signing.ContentHash;
import team4.emotionmap.contracts.signing.SignedClaims;
import team4.emotionmap.contracts.signing.SignedValueCodec;
import team4.emotionmap.contracts.signing.SignedValuePurpose;
import team4.emotionmap.contracts.time.ServiceTime;
import team4.emotionmap.letter.dto.LetterLikeResponse;
import team4.emotionmap.letter.dto.LetterPageResponse;
import team4.emotionmap.letter.dto.LetterReadResponse;
import team4.emotionmap.letter.dto.LetterResponse;
import team4.emotionmap.letter.dto.TodayResponse;
import team4.emotionmap.letter.selection.TodaySelectionStore;
import team4.emotionmap.memory.Memory;
import team4.emotionmap.memory.MemoryAccessService;
import team4.emotionmap.memory.MemoryCategoryRepository;
import team4.emotionmap.memory.MemoryReadAccess;
import team4.emotionmap.memory.MemoryRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class LetterService {

    private static final int CURSOR_VERSION = 1;
    private static final int NO_REQUESTED_LIMIT = -1;
    private static final String CLAIM_CONTEXT = "ctx";
    private static final String CLAIM_AFTER_AT = "afterAt";
    private static final String CLAIM_AFTER_ID = "afterId";
    private static final String CLAIM_UPPER_AT = "upperAt";
    private static final String CLAIM_UPPER_ID = "upperId";
    private static final Set<String> CURSOR_CLAIMS = Set.of(
            CLAIM_CONTEXT, CLAIM_AFTER_AT, CLAIM_AFTER_ID, CLAIM_UPPER_AT, CLAIM_UPPER_ID);
    private static final Pattern LIMIT = Pattern.compile("[0-9]+");
    private static final Pattern CANONICAL_UUID =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    private final LetterDeliveryRepository letterDeliveryRepository;
    private final LetterReadJdbcQuery letterReadJdbcQuery;
    private final LetterResponseAssembler letterResponseAssembler;
    private final TodaySelectionStore todaySelectionStore;
    private final MemorySnapshotReader memorySnapshotReader;
    private final MemoryRepository memoryRepository;
    private final MemoryCategoryRepository memoryCategoryRepository;
    private final MemoryAccessService memoryAccessService;
    private final AccountAccessService accountAccessService;
    private final UserRepository userRepository;
    private final LocalImageStore localImageStore;
    private final ImageFileLifecycle imageFileLifecycle;
    private final ServiceConfigSource serviceConfigSource;
    private final PaginationProperties paginationProperties;
    private final SignedValueCodec signedValueCodec;
    private final Clock clock;

    @Transactional(readOnly = true)
    public LetterPageResponse findForReceiver(UUID receiverId, List<String> atmosphereFilters,
                                              List<String> categoryFilters, String cursor, String limit) {
        accountAccessService.requireAccess(receiverId, "/v1/letters");
        LetterFilter filter = LetterFilter.parse(atmosphereFilters, categoryFilters);
        int requestedLimit = parseRequestedLimit(limit);
        ServiceLimits serviceLimits = serviceConfigSource.limits();
        if (serviceLimits.defaultPageLimit() > serviceLimits.maxPageLimit()) {
            throw ContractError.of(ErrorCode.CONFIGURATION_UNAVAILABLE);
        }
        int effectiveLimit = requestedLimit == NO_REQUESTED_LIMIT
                ? serviceLimits.defaultPageLimit() : requestedLimit;
        if (effectiveLimit < 1 || effectiveLimit > serviceLimits.maxPageLimit()) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST, FieldError.outOfRange("limit"));
        }

        Instant now = clock.instant();
        Instant configuredExpiry = paginationProperties.requireCursorExpiry(now);
        String context = pageContext(receiverId, filter, effectiveLimit);
        CursorState cursorState = cursor == null
                ? new CursorState(null, null, configuredExpiry)
                : decodeCursor(cursor, receiverId, now, context);
        int fetchLimit;
        try {
            fetchLimit = Math.addExact(effectiveLimit, 1);
        } catch (ArithmeticException error) {
            throw ContractError.of(ErrorCode.CONFIGURATION_UNAVAILABLE);
        }

        List<LetterReadJdbcQuery.DeliveryRow> rows = letterReadJdbcQuery.findPage(receiverId, filter,
                cursorState.after(), cursorState.upper(), fetchLimit);
        int pageRowCount = Math.min(effectiveLimit, rows.size());
        List<LetterReadJdbcQuery.DeliveryRow> pageRows = rows.subList(0, pageRowCount);
        Set<UUID> memoryIds = pageRows.stream().map(LetterReadJdbcQuery.DeliveryRow::memoryId)
                .collect(java.util.stream.Collectors.toSet());
        Map<UUID, MemorySnapshot> snapshots = memorySnapshotReader.readAll(memoryIds);

        List<LetterResponse> items = new ArrayList<>(pageRows.size());
        for (LetterReadJdbcQuery.DeliveryRow row : pageRows) {
            MemorySnapshot source = snapshots.get(row.memoryId());
            if (source == null) {
                // A delivery's source is RESTRICT-FK protected. Do not turn corruption into a fake deletion.
                throw new IllegalStateException("delivery source is missing");
            }
            if (filter.isFiltered() && !letterResponseAssembler.isAvailable(source)) {
                // Current safety is rechecked after the filter query; cursor still consumes this row.
                continue;
            }
            items.add(letterResponseAssembler.assemble(row.snapshot(), source));
        }
        return new LetterPageResponse(items, pageInfo(receiverId, rows, effectiveLimit, context, cursorState));
    }

    @Transactional(readOnly = true)
    public TodayResponse today(UUID receiverId) {
        accountAccessService.requireAccess(receiverId, "/v1/letters/today");
        TodaySelectionStore.Snapshot snapshot;
        try {
            snapshot = todaySelectionStore.read(receiverId);
        } catch (DataAccessException error) {
            throw ContractError.withCause(ErrorCode.SERVICE_UNAVAILABLE, error);
        }
        if (snapshot == null || snapshot.serverTime() == null) {
            throw ContractError.of(ErrorCode.SERVICE_UNAVAILABLE);
        }

        Instant serverTime = snapshot.serverTime();
        LocalDate serviceDate = ServiceTime.serviceDate(serverTime);
        Instant scheduledAt = ServiceTime.cutoff(serviceDate);
        Instant nextScheduledAt = ServiceTime.nextScheduledAt(serverTime);
        if (serverTime.isBefore(scheduledAt)) {
            return today(TodayResponse.Status.PENDING, serviceDate, scheduledAt, nextScheduledAt, serverTime,
                    TodayResponse.PendingReason.BEFORE_SCHEDULE, null, null);
        }
        if (!snapshot.eligibleAtCutoff()) {
            return today(TodayResponse.Status.PENDING, serviceDate, scheduledAt, nextScheduledAt, serverTime,
                    TodayResponse.PendingReason.FIRST_DELIVERY_TOMORROW, null, null);
        }

        DailySelectionStatus status = snapshot.status();
        MemoryReadAccess.DeliverySnapshot delivery = snapshot.delivery();
        if (status == null) {
            if (delivery != null) {
                return inconsistent(serviceDate, scheduledAt, nextScheduledAt, serverTime);
            }
            if (!snapshot.configurationAvailableAtCutoff()) {
                throw ContractError.of(ErrorCode.CONFIGURATION_UNAVAILABLE);
            }
            return today(TodayResponse.Status.PENDING, serviceDate, scheduledAt, nextScheduledAt, serverTime,
                    TodayResponse.PendingReason.SCHEDULE_DELAYED, null, null);
        }
        if ((status == DailySelectionStatus.DELIVERED) != (delivery != null)) {
            return inconsistent(serviceDate, scheduledAt, nextScheduledAt, serverTime);
        }

        return switch (status) {
            case PENDING -> today(TodayResponse.Status.PENDING, serviceDate, scheduledAt, nextScheduledAt, serverTime,
                    TodayResponse.PendingReason.SCHEDULE_DELAYED, null, null);
            case PROCESSING -> today(TodayResponse.Status.PENDING, serviceDate, scheduledAt, nextScheduledAt, serverTime,
                    TodayResponse.PendingReason.PROCESSING, null, null);
            case NO_CANDIDATE -> today(TodayResponse.Status.NO_CANDIDATE, serviceDate, scheduledAt,
                    nextScheduledAt, serverTime, null, null, null);
            case DELIVERED -> today(TodayResponse.Status.DELIVERED, serviceDate, scheduledAt,
                    nextScheduledAt, serverTime, null, null, assembleTodayDelivery(delivery));
            case RETRYABLE_ERROR -> today(TodayResponse.Status.RETRYING, serviceDate, scheduledAt,
                    nextScheduledAt, serverTime, null, TodayResponse.ErrorReason.RETRYABLE_FAILURE, null);
            case EXPIRED_ERROR, SKIPPED_ACCESS -> today(TodayResponse.Status.ERROR, serviceDate,
                    scheduledAt, nextScheduledAt, serverTime, null, TodayResponse.ErrorReason.PROCESSING_FAILURE, null);
        };
    }

    @Transactional
    public LetterReadResponse markRead(UUID receiverId, UUID deliveryId) {
        accountAccessService.requireAccess(receiverId, "/v1/letters/" + deliveryId + "/read");
        UUID memoryId = requireDeliveredMemoryId(receiverId, deliveryId);
        // Source precedes delivery everywhere, including concurrent read/delete operations.
        Memory source = memoryRepository.findByIdForUpdate(memoryId).orElse(null);
        LetterDelivery delivery = requireLockedDelivery(receiverId, deliveryId);
        requireAvailable(receiverId, source);
        delivery.markRead(clock.instant());
        return new LetterReadResponse(delivery.getId(), delivery.getReadAt());
    }

    @Transactional
    public LetterLikeResponse like(UUID receiverId, UUID deliveryId) {
        accountAccessService.requireAccess(receiverId, "/v1/letters/" + deliveryId + "/like");
        User receiver = userRepository.findByIdForUpdate(receiverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN"));
        AccountAccessService.requireActive(receiver);
        UUID memoryId = requireDeliveredMemoryId(receiverId, deliveryId);
        // Source precedes delivery everywhere, including concurrent read/delete operations.
        Memory source = memoryRepository.findByIdForUpdate(memoryId).orElse(null);
        LetterDelivery delivery = requireLockedDelivery(receiverId, deliveryId);
        if (delivery.getLikedAt() != null) {
            return new LetterLikeResponse(deliveryId, LetterLikeResponse.Status.ALREADY_COPIED,
                    delivery.getLikedAt(), null);
        }
        requireAvailable(receiverId, source);

        String imagePath = copyImageForTransaction(source.getImagePath());
        Memory copy = memoryRepository.save(source.copyForOwner(receiverId, imagePath));
        memoryCategoryRepository.findByIdMemoryIdOrderBySlotNo(memoryId)
                .forEach(category -> memoryCategoryRepository.save(category.copyForMemory(copy.getId())));
        delivery.markLiked(clock.instant());
        return new LetterLikeResponse(deliveryId, LetterLikeResponse.Status.COPIED,
                delivery.getLikedAt(), copy.getId());
    }

    private LetterResponse assembleTodayDelivery(MemoryReadAccess.DeliverySnapshot delivery) {
        MemorySnapshot source = memorySnapshotReader.readAll(Set.of(delivery.memoryId())).get(delivery.memoryId());
        if (source == null) {
            throw new IllegalStateException("today delivery source is missing");
        }
        return letterResponseAssembler.assemble(delivery, source);
    }

    private static TodayResponse inconsistent(LocalDate serviceDate, Instant scheduledAt, Instant nextScheduledAt,
                                              Instant serverTime) {
        return today(TodayResponse.Status.ERROR, serviceDate, scheduledAt, nextScheduledAt, serverTime,
                null, TodayResponse.ErrorReason.SELECTION_STATE_INCONSISTENT, null);
    }

    private static TodayResponse today(TodayResponse.Status status, LocalDate serviceDate, Instant scheduledAt,
                                       Instant nextScheduledAt, Instant serverTime,
                                       TodayResponse.PendingReason pendingReason,
                                       TodayResponse.ErrorReason errorReason, LetterResponse delivery) {
        return new TodayResponse(status, serviceDate, scheduledAt, nextScheduledAt, serverTime,
                pendingReason, errorReason, delivery);
    }

    private PageInfo pageInfo(UUID receiverId, List<LetterReadJdbcQuery.DeliveryRow> rows, int effectiveLimit,
                              String context, CursorState state) {
        if (rows.size() <= effectiveLimit) {
            return PageInfo.last();
        }
        LetterReadJdbcQuery.PageKey after = rows.get(effectiveLimit - 1).key();
        LetterReadJdbcQuery.PageKey upper = state.upper() == null ? rows.getFirst().key() : state.upper();
        String nextCursor = signedValueCodec.sign(new SignedClaims(SignedValuePurpose.PAGE_CURSOR, receiverId,
                CURSOR_VERSION, state.expiresAt(), Map.of(
                CLAIM_CONTEXT, ContentHash.sha256Hex(context),
                CLAIM_AFTER_AT, after.deliveredAt().toString(),
                CLAIM_AFTER_ID, after.id().toString(),
                CLAIM_UPPER_AT, upper.deliveredAt().toString(),
                CLAIM_UPPER_ID, upper.id().toString()
        )));
        return PageInfo.next(nextCursor);
    }

    private CursorState decodeCursor(String cursor, UUID receiverId, Instant now, String context) {
        if (cursor.isBlank()) {
            throw ContractError.of(ErrorCode.INVALID_CURSOR);
        }
        SignedClaims claims = signedValueCodec.verify(cursor, SignedValuePurpose.PAGE_CURSOR, receiverId,
                CURSOR_VERSION, now);
        String contextHash = claims.claim(CLAIM_CONTEXT);
        if (contextHash == null || !SHA256_HEX.matcher(contextHash).matches()
                || !claims.claims().keySet().equals(CURSOR_CLAIMS)) {
            throw ContractError.of(ErrorCode.INVALID_CURSOR);
        }
        if (!ContentHash.matches(context, contextHash)) {
            throw ContractError.of(ErrorCode.CURSOR_CONTEXT_MISMATCH);
        }
        LetterReadJdbcQuery.PageKey after = new LetterReadJdbcQuery.PageKey(
                canonicalInstant(claims, CLAIM_AFTER_AT), canonicalUuid(claims, CLAIM_AFTER_ID));
        LetterReadJdbcQuery.PageKey upper = new LetterReadJdbcQuery.PageKey(
                canonicalInstant(claims, CLAIM_UPPER_AT), canonicalUuid(claims, CLAIM_UPPER_ID));
        if (compareKey(after, upper) > 0) {
            throw ContractError.of(ErrorCode.INVALID_CURSOR);
        }
        return new CursorState(after, upper, claims.expiresAt());
    }

    private static Instant canonicalInstant(SignedClaims claims, String name) {
        String value = claims.claim(name);
        if (value == null) {
            throw ContractError.of(ErrorCode.INVALID_CURSOR);
        }
        try {
            Instant parsed = Instant.parse(value);
            if (!parsed.toString().equals(value)) {
                throw ContractError.of(ErrorCode.INVALID_CURSOR);
            }
            return parsed;
        } catch (DateTimeException error) {
            throw ContractError.of(ErrorCode.INVALID_CURSOR);
        }
    }

    private static UUID canonicalUuid(SignedClaims claims, String name) {
        String value = claims.claim(name);
        if (value == null || !CANONICAL_UUID.matcher(value).matches()) {
            throw ContractError.of(ErrorCode.INVALID_CURSOR);
        }
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value)) {
                throw ContractError.of(ErrorCode.INVALID_CURSOR);
            }
            return parsed;
        } catch (IllegalArgumentException error) {
            throw ContractError.of(ErrorCode.INVALID_CURSOR);
        }
    }

    private static int compareKey(LetterReadJdbcQuery.PageKey left, LetterReadJdbcQuery.PageKey right) {
        int timestamp = left.deliveredAt().compareTo(right.deliveredAt());
        return timestamp != 0 ? timestamp : comparePgUuid(left.id(), right.id());
    }

    private static int comparePgUuid(UUID left, UUID right) {
        int mostSignificant = Long.compareUnsigned(left.getMostSignificantBits(), right.getMostSignificantBits());
        return mostSignificant != 0 ? mostSignificant
                : Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
    }

    private static int parseRequestedLimit(String limit) {
        if (limit == null) {
            return NO_REQUESTED_LIMIT;
        }
        if (!LIMIT.matcher(limit).matches()) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST, FieldError.invalid("limit"));
        }
        try {
            return Integer.parseInt(limit);
        } catch (NumberFormatException error) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST, FieldError.invalid("limit"));
        }
    }

    private static String pageContext(UUID receiverId, LetterFilter filter, int effectiveLimit) {
        return "letters-page-v1\n"
                + "actor=" + receiverId + "\n"
                + "path=/v1/letters\n"
                + "filter=" + filter.canonical() + "\n"
                + "sort=delivered-at-desc,id-desc\n"
                + "limit=" + effectiveLimit;
    }

    private UUID requireDeliveredMemoryId(UUID receiverId, UUID deliveryId) {
        return letterDeliveryRepository.findMemoryIdByIdAndReceiverId(deliveryId, receiverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND"));
    }

    private LetterDelivery requireLockedDelivery(UUID receiverId, UUID deliveryId) {
        return letterDeliveryRepository.findByIdAndReceiverIdForUpdate(deliveryId, receiverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND"));
    }

    private void requireAvailable(UUID receiverId, Memory source) {
        if (source == null || source.getDistributionType() != DistributionType.LETTER
                || source.getOwnerId().equals(receiverId)
                || !memoryAccessService.isReadable(receiverId, source)) {
            throw unavailable();
        }
    }

    private String copyImageForTransaction(String sourcePath) {
        if (sourcePath == null) {
            return null;
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Image copies require a synchronized database transaction");
        }
        imageFileLifecycle.protectWritesInCurrentTransaction();
        String copyPath = localImageStore.duplicateIndependent(sourcePath).storageKey();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                // An unknown outcome may have committed: never delete its potentially referenced file.
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        imageFileLifecycle.deleteIfUnreferenced(copyPath);
                    } catch (RuntimeException exception) {
                        log.warn("Failed to clean up an image after a rolled-back letter copy");
                    }
                }
            }
        });
        return copyPath;
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.GONE, "MEMORY_UNAVAILABLE");
    }

    private record CursorState(LetterReadJdbcQuery.PageKey after, LetterReadJdbcQuery.PageKey upper,
                               Instant expiresAt) {
    }
}
