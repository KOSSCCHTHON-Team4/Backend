package team4.emotionmap.memory;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import team4.emotionmap.account.AccountAccessService;
import team4.emotionmap.account.User;
import team4.emotionmap.account.UserRepository;
import team4.emotionmap.contracts.ai.AnalysisEnrichment;
import team4.emotionmap.contracts.config.MatchingProperties;
import team4.emotionmap.contracts.config.ServiceConfigSource;
import team4.emotionmap.contracts.dictionary.CategorySelection;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.error.FieldError;
import team4.emotionmap.contracts.geo.DistanceMeters;
import team4.emotionmap.contracts.geo.GeoPoint;
import team4.emotionmap.contracts.media.LocalImageStore;
import team4.emotionmap.contracts.memory.AtmosphereAnalysisStatus;
import team4.emotionmap.contracts.memory.AtmosphereSources;
import team4.emotionmap.contracts.memory.CategoryAnalysisStatus;
import team4.emotionmap.contracts.memory.CategoryAssignment;
import team4.emotionmap.contracts.memory.ContentStatus;
import team4.emotionmap.contracts.memory.DataOrigin;
import team4.emotionmap.contracts.memory.DistributionType;
import team4.emotionmap.contracts.memory.ModerationStatus;
import team4.emotionmap.contracts.memory.OriginKind;
import team4.emotionmap.contracts.request.RequestCoordinator;
import team4.emotionmap.contracts.time.SelectionPublicationBarrier;
import team4.emotionmap.contracts.validation.TextRules;
import team4.emotionmap.media.ImageUpload;
import team4.emotionmap.media.ImageUploadService;
import team4.emotionmap.memory.analysis.AnalysisReceipt;
import team4.emotionmap.memory.analysis.AnalysisReceiptCodec;
import team4.emotionmap.memory.analysis.AxisSourceResolver;
import team4.emotionmap.memory.dto.MemoryCreateRequest;
import team4.emotionmap.place.NaverCategoryMapper;
import team4.emotionmap.place.Place;
import team4.emotionmap.place.PlaceCategory;
import team4.emotionmap.place.PlaceCategoryRepository;
import team4.emotionmap.place.PlaceRepository;
/**
 * A05 직접 경험 생성(기획 §1 "[리뷰 작성] → /ai/analyze → Memory 저장 → Place 갱신").
 * <ul>
 *   <li>analysisToken 이 있으면 서명·사용자·본문(원문 또는 AI 마스킹 본문)·만료를 검증하고, 최종값과 AI 제안을 비교해
 *       축/카테고리 출처(AI/USER)를 서버가 정한다. 없으면 USER/NOT_RUN.</li>
 *   <li>카테고리: 네이버 등록 장소는 매핑이 우선. 미등록 장소는 요청 카테고리(사용자 확정) 그대로.</li>
 *   <li>장소: placeId 지정 시 가시성·좌표 일치 확인. 미지정 시 네이버 상호명이 같은 근처 핀 또는 반경 20m 안 같은 카테고리
 *       미등록 핀과 병합(기획 §5), 없으면 새 핀.</li>
 *   <li>PRIVATE 는 저장 즉시 장소 프로필 재계산. LETTER 는 PENDING 으로 저장되고 커밋 뒤 안전 검사가 이어진다.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService {
    private final MemoryRepository memoryRepository;
    private final MemoryCategoryRepository memoryCategoryRepository;
    private final MemoryAccessService memoryAccessService;
    private final UserRepository userRepository;
    private final AccountAccessService accountAccessService;
    private final PlaceRepository placeRepository;
    private final PlaceCategoryRepository placeCategoryRepository;
    private final ImageUploadService imageUploadService;
    private final LocalImageStore localImageStore;
    private final AnalysisReceiptCodec receiptCodec;
    private final PlaceProfileService placeProfileService;
    private final MemoryWriteQueries memoryWriteQueries;
    private final ServiceConfigSource serviceConfig;
    private final MatchingProperties matching;
    private final RequestCoordinator requestCoordinator;
    private final SelectionPublicationBarrier publicationBarrier;
    private final Clock clock;

    /**
     * Claims the durable idempotency key before any expiring token, staged image, or quota is consumed.
     * The coordinator owns the only transaction that creates and completes a memory request.
     */
    public CreateResult create(UUID ownerId, UUID key, MemoryCreateRequest request) {
        requireCurrentOwner(ownerId);
        RequestCoordinator.Scope scope = new RequestCoordinator.Scope(
                ownerId, RequestCoordinator.Route.MEMORIES, key);
        RequestCoordinator.Admission admission = requestCoordinator.claim(
                scope, MemoryRequestFingerprint.fingerprint(request));
        if (admission instanceof RequestCoordinator.Replay replay) {
            return receiptFor(ownerId, replay.resource(), RequestCoordinator.CompletionKind.REPLAY);
        }

        RequestCoordinator.Claim claim = ((RequestCoordinator.Claimed) admission).claim();
        RequestCoordinator.Completion completion = requestCoordinator.complete(
                claim, () -> persistNewMemory(ownerId, request));
        return receiptFor(ownerId, completion.resource(), completion.kind());
    }

    private RequestCoordinator.ResourceRef persistNewMemory(UUID ownerId, MemoryCreateRequest request) {
        requireLockedCurrentOwner(ownerId);
        if (request.type() == null || request.atmospheres() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        }
        String content = TextRules.requireContent(request.content(),
                serviceConfig.limits().memoryContentMaxCodePoints(), "content", ErrorCode.VALIDATION_ERROR);
        Instant now = publicationBarrier.databaseNow();
        AnalysisReceipt receipt = request.analysisToken() == null ? null
                : receiptCodec.verify(request.analysisToken(), ownerId, content, now);

        // 카테고리: 사용자 확정값(0~3). 네이버 카테고리가 있으면 그 매핑이 1슬롯이 된다.
        List<PlaceCategoryCode> finalCodes = CategorySelection.validate("categoryCodes", request.categoryCodes());
        PlaceCategoryCode naverMapped = NaverCategoryMapper
                .map(MemoryRequestFingerprint.normalizeNaverField(request.naverCategory())).orElse(null);
        if (naverMapped != null && !finalCodes.contains(naverMapped)) {
            List<PlaceCategoryCode> merged = new java.util.ArrayList<>();
            merged.add(naverMapped);
            for (PlaceCategoryCode category : finalCodes) {
                if (merged.size() < PlaceCategoryCode.MAX_PER_MEMORY) {
                    merged.add(category);
                }
            }
            finalCodes = List.copyOf(merged);
        }
        List<CategoryAssignment> assignments = AxisSourceResolver.resolveCategories(finalCodes,
                receipt == null ? null : receipt.categories());
        AtmosphereSources sources = AxisSourceResolver.resolveAxes(request.atmospheres(),
                receipt == null ? null : receipt.atmospheres());

        Place place = resolvePlace(ownerId, request, finalCodes.isEmpty() ? null : finalCodes.getFirst(), naverMapped);
        ImageUpload image = request.imageId() == null ? null
                : imageUploadService.requireAttachable(ownerId, request.imageId());
        if (memoryWriteQueries.countDirectForServiceDay(ownerId, now)
                >= serviceConfig.limits().dailyDirectMemoryLimit()) {
            throw ContractError.of(ErrorCode.DAILY_WRITE_LIMIT_EXCEEDED);
        }

        AnalysisEnrichment enrichment = receipt == null ? AnalysisEnrichment.NONE : receipt.enrichment();
        boolean piiMasked = receipt != null && receipt.isMaskedSubmission(content);
        PlaceCategoryCode predicted = receipt == null || receipt.categories().isEmpty()
                ? null : receipt.categories().getFirst();

        Memory memory = memoryRepository.save(Memory.builder()
                .ownerId(ownerId).placeId(place.getId()).content(content)
                .distributionType(request.type()).originKind(OriginKind.DIRECT)
                .dataOrigin(DataOrigin.PARTICIPANT)
                .placeLabelSnapshot(place.displayName()).placeLat(place.getLat()).placeLng(place.getLng())
                .crowdLevel(request.atmospheres().crowdLevel())
                .spatialFeel(request.atmospheres().spatialFeel())
                .companyFit(request.atmospheres().companyFit())
                .stayStyle(request.atmospheres().stayStyle())
                .crowdSource(sources.crowdLevel()).spatialSource(sources.spatialFeel())
                .companySource(sources.companyFit()).staySource(sources.stayStyle())
                .atmosphereAnalysisStatus(receipt == null ? AtmosphereAnalysisStatus.NOT_RUN : receipt.atmosphereStatus())
                .categoryAnalysisStatus(receipt == null ? CategoryAnalysisStatus.NOT_RUN : receipt.categoryStatus())
                .analysisModel(receipt == null ? null : receipt.provenance().model())
                .analysisPromptVersion(receipt == null ? null : receipt.provenance().promptVersion())
                .imagePath(image == null ? null : image.getStoragePath())
                .imageMediaType(image == null ? null : image.getMediaType())
                .imageSizeBytes(image == null ? null : image.getSizeBytes())
                .moderationStatus(request.type() == DistributionType.PRIVATE
                        ? ModerationStatus.NOT_REQUIRED : ModerationStatus.PENDING)
                .evidence(enrichment.evidence().isEmpty() ? null : enrichment.evidence())
                .tags(enrichment.tags().isEmpty() ? null : enrichment.tags())
                .categoryPred(predicted == null ? null : predicted.name())
                .categoryConf(enrichment.categoryConfidence())
                .safe(enrichment.safe())
                .piiMasked(piiMasked)
                .unsafeReason(enrichment.unsafeReason())
                .createdAt(now)
                .build());
        if (image != null) {
            image.attach(memory.getId());
        }
        for (CategoryAssignment assignment : assignments) {
            PlaceCategory category = placeCategoryRepository.findByCode(assignment.code().name())
                    .orElseThrow(() -> new IllegalStateException("Category seed missing: " + assignment.code()));
            memoryCategoryRepository.save(MemoryCategory.builder()
                    .id(new MemoryCategoryId(memory.getId(), category.getId()))
                    .slotNo((short) assignment.slotNo()).assignmentSource(assignment.source())
                    .labelSnapshot(category.getLabel()).taxonomyVersion(category.getTaxonomyVersion()).build());
        }
        if (request.type() == DistributionType.PRIVATE) {
            placeProfileService.recompute(place.getId()); // LETTER 는 안전 승인 시점에 반영한다.
        }
        log.info("memory created id={} type={} place={} analysis={} pii={}", memory.getId(), request.type(),
                place.getId(), receipt != null, piiMasked);
        return new RequestCoordinator.ResourceRef(RequestCoordinator.Route.MEMORIES, memory.getId());
    }

    private CreateResult receiptFor(
            UUID ownerId, RequestCoordinator.ResourceRef resource, RequestCoordinator.CompletionKind kind
    ) {
        if (resource.route() != RequestCoordinator.Route.MEMORIES) {
            throw ContractError.of(ErrorCode.SERVICE_UNAVAILABLE);
        }
        requireCurrentOwner(ownerId);
        return new CreateResult(resource.id(), kind);
    }

    public ImageContent image(UUID userId, UUID memoryId) {
        Memory memory = memoryAccessService.requireReadable(userId, memoryId);
        if (memory.getImagePath() == null) {
            throw ContractError.of(ErrorCode.RESOURCE_NOT_FOUND);
        }
        String mediaType = memory.getImageMediaType();
        long sizeBytes = memory.getImageSizeBytes();
        try {
            InputStream content = localImageStore.open(memory.getImagePath());
            return new ImageContent(content, mediaType, sizeBytes);
        } catch (ContractError error) {
            if (error.code() == ErrorCode.RESOURCE_NOT_FOUND) {
                throw ContractError.of(ErrorCode.IMAGE_FILE_UNAVAILABLE);
            }
            throw error;
        }
    }

    public record ImageContent(InputStream content, String mediaType, long sizeBytes) {
    }

    @Transactional
    public void delete(UUID ownerId, UUID id) {
        requireActiveOwner(ownerId);
        Memory memory = memoryRepository.findByIdForUpdate(id)
                .filter(candidate -> candidate.getOwnerId().equals(ownerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memory not found"));
        if (memory.getContentStatus() != ContentStatus.DELETED) {
            memory.softDelete(clock.instant());
            if (PlaceProfileService.countsAsReview(memory.toBuilder().contentStatus(ContentStatus.ACTIVE).build())) {
                placeProfileService.recompute(memory.getPlaceId());
            }
        }
    }

    private void requireActiveOwner(UUID ownerId) {
        User owner = userRepository.findByIdForUpdate(ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
        AccountAccessService.requireActive(owner);
    }

    private void requireCurrentOwner(UUID ownerId) {
        accountAccessService.requireAccess(ownerId, RequestCoordinator.Route.MEMORIES.path());
    }

    private void requireLockedCurrentOwner(UUID ownerId) {
        User owner = userRepository.findByIdForUpdate(ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
        AccountAccessService.requireActive(owner);
        accountAccessService.requireAccess(ownerId, RequestCoordinator.Route.MEMORIES.path());
    }

    /**
     * 핀 결정. 명시 placeId → 가시성·좌표 일치 검사. 미지정 → (1) 네이버 상호명이 같은 근처 핀,
     * (2) 미등록이면 반경 {@code mergeDistanceMeters} 안 같은 카테고리 핀과 병합, (3) 새 핀.
     */
    private Place resolvePlace(UUID ownerId, MemoryCreateRequest request, PlaceCategoryCode category,
                               PlaceCategoryCode naverMapped) {
        GeoPoint point = MemoryRequestFingerprint.normalizedCoordinates(request);
        if (request.placeId() != null) {
            boolean visible = memoryRepository
                    .findByPlaceIdAndContentStatusOrderByCreatedAtDesc(request.placeId(), ContentStatus.ACTIVE).stream()
                    .anyMatch(memory -> memoryAccessService.isReadable(ownerId, memory));
            Place place = placeRepository.findById(request.placeId()).filter(p -> visible)
                    .orElseThrow(() -> ContractError.of(ErrorCode.RESOURCE_NOT_FOUND));
            if (place.getLat().doubleValue() != point.lat() || place.getLng().doubleValue() != point.lng()) {
                throw ContractError.of(ErrorCode.PLACE_COORDINATE_MISMATCH,
                        FieldError.invalid("lat"), FieldError.invalid("lng"));
            }
            return place;
        }

        double mergeRadius = matching.mergeDistanceMeters();
        List<Place> nearby = placeRepository.findAllInBox(
                point.lat() - mergeRadius / 111_320.0, point.lat() + mergeRadius / 111_320.0,
                point.lng() - mergeRadius / (111_320.0 * Math.cos(Math.toRadians(point.lat()))),
                point.lng() + mergeRadius / (111_320.0 * Math.cos(Math.toRadians(point.lat()))))
                .stream().filter(p -> DistanceMeters.between(point, new GeoPoint(p.getLat(), p.getLng())) <= mergeRadius)
                .toList();

        String title = MemoryRequestFingerprint.normalizeNaverTitle(request.naverTitle());
        if (title != null) {
            for (Place p : nearby) {
                if (title.equals(p.getNaverTitle())) {
                    return p;
                }
            }
            Place created = placeRepository.save(Place.builder().lat(point.lat()).lng(point.lng())
                    .label(MemoryRequestFingerprint.normalizeLabel(request.placeLabel())).build());
            created.attachNaver(title, MemoryRequestFingerprint.normalizeNaverField(request.naverAddress()),
                    MemoryRequestFingerprint.normalizeNaverField(request.naverCategory()), naverMapped);
            return created;
        }
        // 미등록 장소: 20m 안 같은 카테고리의 미등록 핀이 있으면 병합(기획 §5)
        if (category != null) {
            for (Place p : nearby) {
                if (!p.isNaverRegistered() && p.category() == category) {
                    return p;
                }
            }
        }
        return placeRepository.save(Place.builder().lat(point.lat()).lng(point.lng())
                .label(MemoryRequestFingerprint.normalizeLabel(request.placeLabel())).build());
    }

    public record CreateResult(UUID memoryId, RequestCoordinator.CompletionKind kind) {
    }
}
