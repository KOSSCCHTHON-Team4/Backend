package team4.emotionmap.memory;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import team4.emotionmap.account.AccountAccessService;
import team4.emotionmap.contracts.config.PaginationProperties;
import team4.emotionmap.contracts.config.ServiceConfigSource;
import team4.emotionmap.contracts.config.ServiceLimits;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.error.FieldError;
import team4.emotionmap.contracts.memory.MemorySnapshot;
import team4.emotionmap.contracts.memory.MemorySnapshotReader;
import team4.emotionmap.contracts.page.PageInfo;
import team4.emotionmap.contracts.signing.ContentHash;
import team4.emotionmap.contracts.signing.SignedClaims;
import team4.emotionmap.contracts.signing.SignedValueCodec;
import team4.emotionmap.contracts.signing.SignedValuePurpose;
import team4.emotionmap.memory.dto.MemoryPageResponse;
import team4.emotionmap.memory.dto.MemoryResponse;

/**
 * The sole owner of HTTP memory projections. It bulk-loads source snapshots, delivery metadata,
 * and owner like counts; neither a card nor a page performs a repository lookup per item.
 */
@Service
@RequiredArgsConstructor
public class MemoryQueryService {

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

    private final AccountAccessService accountAccessService;
    private final MemoryReadJdbcQuery memoryReadJdbcQuery;
    private final MemorySnapshotReader memorySnapshotReader;
    private final MemoryReadAccess memoryReadAccess;
    private final MemoryAccessService memoryAccessService;
    private final ServiceConfigSource serviceConfigSource;
    private final PaginationProperties paginationProperties;
    private final SignedValueCodec signedValueCodec;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MemoryResponse get(UUID viewerId, UUID memoryId) {
        accountAccessService.requireAccess(viewerId, "/v1/memories/" + memoryId);
        MemorySnapshot memory = memorySnapshotReader.readAll(Set.of(memoryId)).get(memoryId);
        if (memory == null) {
            throw notFound();
        }
        MemoryReadAccess.DeliverySnapshot delivery = memoryReadAccess
                .findDeliveries(viewerId, Set.of(memoryId)).get(memoryId);
        MemoryAccessService.ReadableRole role = memoryAccessService.requireReadable(viewerId, memory, delivery);
        long likeCount = role == MemoryAccessService.ReadableRole.OWNER
                ? requiredLikeCount(memoryReadAccess.countLikes(Set.of(memoryId)), memoryId) : 0L;
        return project(memory, role, delivery, likeCount);
    }

    @Transactional(readOnly = true)
    public MemoryPageResponse bookmarks(UUID viewerId, String cursor, String limit) {
        return page(viewerId, cursor, limit, PageKind.BOOKMARKS, null);
    }

    @Transactional(readOnly = true)
    public MemoryPageResponse ownLetters(UUID viewerId, String type, String cursor, String limit) {
        if (!"LETTER".equals(type)) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST, FieldError.invalid("type"));
        }
        return page(viewerId, cursor, limit, PageKind.OWN_LETTERS, null);
    }

    @Transactional(readOnly = true)
    public MemoryPageResponse forPlace(UUID viewerId, UUID placeId, String cursor, String limit) {
        return page(viewerId, cursor, limit, PageKind.PLACE, placeId);
    }

    private MemoryPageResponse page(UUID viewerId, String cursor, String limit, PageKind kind, UUID placeId) {
        String path = kind.path(placeId);
        accountAccessService.requireAccess(viewerId, path);
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
        String context = pageContext(viewerId, kind, placeId, effectiveLimit);
        CursorState cursorState = cursor == null
                ? new CursorState(null, null, configuredExpiry)
                : decodeCursor(cursor, viewerId, now, context);
        int fetchLimit;
        try {
            fetchLimit = Math.addExact(effectiveLimit, 1);
        } catch (ArithmeticException error) {
            throw ContractError.of(ErrorCode.CONFIGURATION_UNAVAILABLE);
        }

        List<MemoryReadJdbcQuery.PageRow> rows = switch (kind) {
            case BOOKMARKS -> memoryReadJdbcQuery.bookmarks(viewerId, cursorState.after(), cursorState.upper(), fetchLimit);
            case OWN_LETTERS -> memoryReadJdbcQuery.ownLetters(viewerId, cursorState.after(), cursorState.upper(), fetchLimit);
            case PLACE -> memoryReadJdbcQuery.forPlace(viewerId, placeId, cursorState.after(), cursorState.upper(), fetchLimit);
        };
        if (rows.isEmpty() && cursor == null && kind == PageKind.PLACE) {
            throw notFound();
        }

        int pageRowCount = Math.min(effectiveLimit, rows.size());
        List<MemoryReadJdbcQuery.PageRow> pageRows = rows.subList(0, pageRowCount);
        Set<UUID> memoryIds = pageRows.stream().map(MemoryReadJdbcQuery.PageRow::id).collect(java.util.stream.Collectors.toSet());
        Map<UUID, MemorySnapshot> snapshots = memorySnapshotReader.readAll(memoryIds);
        Map<UUID, MemoryReadAccess.DeliverySnapshot> deliveries = memoryReadAccess.findDeliveries(viewerId, memoryIds);
        Set<UUID> ownedIds = snapshots.values().stream()
                .filter(memory -> viewerId.equals(memory.ownerId()))
                .map(MemorySnapshot::id)
                .collect(java.util.stream.Collectors.toSet());
        Map<UUID, Long> likes = ownedIds.isEmpty() ? Map.of() : memoryReadAccess.countLikes(ownedIds);

        List<MemoryResponse> items = new ArrayList<>(pageRows.size());
        for (MemoryReadJdbcQuery.PageRow row : pageRows) {
            MemorySnapshot snapshot = snapshots.get(row.id());
            if (snapshot == null) {
                // A concurrent physical removal cannot normally happen (FKs), but never fabricate it.
                continue;
            }
            MemoryReadAccess.DeliverySnapshot delivery = deliveries.get(row.id());
            MemoryAccessService.ReadableRole role = memoryAccessService.readableRole(viewerId, snapshot, delivery);
            if (role == null) {
                // Recheck current policy after the SQL page predicate; the cursor still consumes this row.
                continue;
            }
            long likeCount = role == MemoryAccessService.ReadableRole.OWNER
                    ? requiredLikeCount(likes, snapshot.id()) : 0L;
            items.add(project(snapshot, role, delivery, likeCount));
        }
        return new MemoryPageResponse(items, pageInfo(viewerId, rows, effectiveLimit, context, cursorState));
    }

    private MemoryResponse project(MemorySnapshot snapshot, MemoryAccessService.ReadableRole role,
                                   MemoryReadAccess.DeliverySnapshot delivery, long likeCount) {
        List<PlaceCategoryCode> categories = snapshot.categories().stream().map(assignment -> assignment.code()).toList();
        MemoryResponse.CategoryStatus categoryStatus = categories.isEmpty()
                ? MemoryResponse.CategoryStatus.UNCLASSIFIED : MemoryResponse.CategoryStatus.CLASSIFIED;
        MemoryResponse.ViewerRole viewerRole = role == MemoryAccessService.ReadableRole.OWNER
                ? MemoryResponse.ViewerRole.OWNER : MemoryResponse.ViewerRole.RECIPIENT;
        MemoryResponse.OwnerState ownerState = viewerRole == MemoryResponse.ViewerRole.OWNER
                ? new MemoryResponse.OwnerState(snapshot.moderationStatus(), snapshot.availableAt(), likeCount,
                new MemoryResponse.Analysis(snapshot.atmosphereAnalysisStatus(), snapshot.categoryAnalysisStatus()))
                : null;
        MemoryResponse.Delivery deliveryResponse = viewerRole == MemoryResponse.ViewerRole.RECIPIENT
                ? deliveryResponse(delivery) : null;
        return new MemoryResponse(snapshot.id(), snapshot.distributionType(), snapshot.originKind(), snapshot.dataOrigin(),
                snapshot.content(), snapshot.hasImage() ? "/v1/memories/" + snapshot.id() + "/image" : null,
                snapshot.atmospheres(), categories, categoryStatus,
                new MemoryResponse.PlaceSnapshot(snapshot.placeId(), snapshot.location().lat(), snapshot.location().lng(),
                        snapshot.placeLabelSnapshot()),
                snapshot.createdAt(), viewerRole, ownerState, deliveryResponse);
    }

    private static MemoryResponse.Delivery deliveryResponse(MemoryReadAccess.DeliverySnapshot delivery) {
        if (delivery == null) {
            throw new IllegalStateException("recipient projection requires delivery metadata");
        }
        return new MemoryResponse.Delivery(delivery.deliveryId(), delivery.serviceDate(), delivery.deliveredAt(),
                delivery.readAt(), delivery.likedAt());
    }

    private static long requiredLikeCount(Map<UUID, Long> likeCounts, UUID memoryId) {
        Long count = likeCounts.get(memoryId);
        if (count == null || count < 0) {
            throw new IllegalStateException("missing or invalid bulk like count");
        }
        return count;
    }

    private PageInfo pageInfo(UUID viewerId, List<MemoryReadJdbcQuery.PageRow> rows, int effectiveLimit,
                              String context, CursorState state) {
        if (rows.size() <= effectiveLimit) {
            return PageInfo.last();
        }
        MemoryReadJdbcQuery.PageKey after = rows.get(effectiveLimit - 1).key();
        MemoryReadJdbcQuery.PageKey upper = state.upper() == null ? rows.getFirst().key() : state.upper();
        String nextCursor = signedValueCodec.sign(new SignedClaims(SignedValuePurpose.PAGE_CURSOR, viewerId,
                CURSOR_VERSION, state.expiresAt(), Map.of(
                CLAIM_CONTEXT, ContentHash.sha256Hex(context),
                CLAIM_AFTER_AT, after.createdAt().toString(),
                CLAIM_AFTER_ID, after.id().toString(),
                CLAIM_UPPER_AT, upper.createdAt().toString(),
                CLAIM_UPPER_ID, upper.id().toString()
        )));
        return PageInfo.next(nextCursor);
    }

    private CursorState decodeCursor(String cursor, UUID viewerId, Instant now, String context) {
        if (cursor.isBlank()) {
            throw ContractError.of(ErrorCode.INVALID_CURSOR);
        }
        SignedClaims claims = signedValueCodec.verify(cursor, SignedValuePurpose.PAGE_CURSOR, viewerId,
                CURSOR_VERSION, now);
        String contextHash = claims.claim(CLAIM_CONTEXT);
        if (contextHash == null || !SHA256_HEX.matcher(contextHash).matches()
                || !claims.claims().keySet().equals(CURSOR_CLAIMS)) {
            throw ContractError.of(ErrorCode.INVALID_CURSOR);
        }
        if (!ContentHash.matches(context, contextHash)) {
            throw ContractError.of(ErrorCode.CURSOR_CONTEXT_MISMATCH);
        }
        MemoryReadJdbcQuery.PageKey after = new MemoryReadJdbcQuery.PageKey(
                canonicalInstant(claims, CLAIM_AFTER_AT), canonicalUuid(claims, CLAIM_AFTER_ID));
        MemoryReadJdbcQuery.PageKey upper = new MemoryReadJdbcQuery.PageKey(
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

    private static int compareKey(MemoryReadJdbcQuery.PageKey left, MemoryReadJdbcQuery.PageKey right) {
        int timestamp = left.createdAt().compareTo(right.createdAt());
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

    private static String pageContext(UUID viewerId, PageKind kind, UUID placeId, int effectiveLimit) {
        return "memory-page-v1\n"
                + "actor=" + viewerId + "\n"
                + "path=" + kind.path(placeId) + "\n"
                + "scope=" + kind.scope(placeId) + "\n"
                + "filter=none\n"
                + "sort=created-at-desc,id-desc\n"
                + "limit=" + effectiveLimit;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }

    private enum PageKind {
        BOOKMARKS,
        OWN_LETTERS,
        PLACE;

        private String path(UUID placeId) {
            return switch (this) {
                case BOOKMARKS -> "/v1/bookmarks";
                case OWN_LETTERS -> "/v1/users/me/memories";
                case PLACE -> "/v1/places/" + placeId + "/memories";
            };
        }

        private String scope(UUID placeId) {
            return switch (this) {
                case BOOKMARKS -> "owner-private";
                case OWN_LETTERS -> "owner-letter";
                case PLACE -> "place=" + placeId;
            };
        }
    }

    private record CursorState(MemoryReadJdbcQuery.PageKey after, MemoryReadJdbcQuery.PageKey upper,
                               Instant expiresAt) {
    }
}
