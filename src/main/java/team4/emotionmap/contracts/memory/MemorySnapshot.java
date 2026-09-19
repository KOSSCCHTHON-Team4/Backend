package team4.emotionmap.contracts.memory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import team4.emotionmap.contracts.dictionary.Atmospheres;
import team4.emotionmap.contracts.geo.GeoPoint;

/**
 * 잠금 후 읽은 경험 한 행의 내부 스냅샷. <b>내부 전용</b>이다 — 수신자용 응답 DTO 로 그대로 직렬화하지
 * 않는다(ownerId 등 익명 위반, 불변 규칙 4). BE2 의 like/삭제 use case 가 사본 생성·상태 판정에 쓴다.
 *
 * <p>어떤 형태의 원문↔사본 연결 필드도 없다. 사본을 만들 때 이 스냅샷의 id 는 호출 인자로만 잠시 들고
 * 저장값에는 넣지 않는다(불변 규칙 3).
 */
public record MemorySnapshot(
        UUID id,
        UUID ownerId,
        UUID placeId,
        DistributionType distributionType,
        OriginKind originKind,
        DataOrigin dataOrigin,
        ContentStatus contentStatus,
        ModerationStatus moderationStatus,
        Instant availableAt,
        String content,
        String placeLabelSnapshot,
        GeoPoint location,
        Atmospheres atmospheres,
        AtmosphereSources atmosphereSources,
        int axisDefinitionVersion,
        AtmosphereAnalysisStatus atmosphereAnalysisStatus,
        CategoryAnalysisStatus categoryAnalysisStatus,
        String analysisModel,
        String analysisPromptVersion,
        List<CategoryAssignment> categories,
        ImageAttachment image,
        Instant createdAt,
        Instant deletedAt
) {
    public MemorySnapshot {
        Objects.requireNonNull(id);
        Objects.requireNonNull(ownerId);
        Objects.requireNonNull(placeId);
        Objects.requireNonNull(distributionType);
        Objects.requireNonNull(originKind);
        Objects.requireNonNull(dataOrigin);
        Objects.requireNonNull(contentStatus);
        Objects.requireNonNull(moderationStatus);
        Objects.requireNonNull(content);
        Objects.requireNonNull(location);
        Objects.requireNonNull(atmospheres);
        Objects.requireNonNull(atmosphereSources);
        Objects.requireNonNull(atmosphereAnalysisStatus);
        Objects.requireNonNull(categoryAnalysisStatus);
        Objects.requireNonNull(createdAt);
        categories = categories == null ? List.of() : List.copyOf(categories);
        if (categories.size() > team4.emotionmap.contracts.dictionary.PlaceCategoryCode.MAX_PER_MEMORY) {
            throw new IllegalArgumentException("too many categories");
        }
        if (distributionType == DistributionType.PRIVATE && availableAt != null) {
            throw new IllegalArgumentException("PRIVATE memories never have available_at");
        }
    }

    public boolean hasImage() {
        return image != null;
    }

    /** 활성 LETTER 이고 안전 승인·최초 available_at 이 있는 경우만 후보 자격. 반경·수신 조건은 BE2 가 추가 적용. */
    public boolean isDeliveryCandidateBase() {
        return distributionType == DistributionType.LETTER
                && contentStatus == ContentStatus.ACTIVE
                && moderationStatus.allowsDelivery()
                && availableAt != null;
    }
}
