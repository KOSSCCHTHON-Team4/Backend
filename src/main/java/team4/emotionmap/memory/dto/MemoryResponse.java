package team4.emotionmap.memory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import team4.emotionmap.contracts.dictionary.Atmospheres;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;
import team4.emotionmap.contracts.memory.AtmosphereAnalysisStatus;
import team4.emotionmap.contracts.memory.CategoryAnalysisStatus;
import team4.emotionmap.contracts.memory.DataOrigin;
import team4.emotionmap.contracts.memory.DistributionType;
import team4.emotionmap.contracts.memory.ModerationStatus;
import team4.emotionmap.contracts.memory.OriginKind;

/**
 * Role-specific memory projection. It intentionally has neither author identity nor storage keys.
 */
public record MemoryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DistributionType type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OriginKind originKind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DataOrigin dataOrigin,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String content,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String imageUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Atmospheres atmospheres,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PlaceCategoryCode> categories,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CategoryStatus categoryStatus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PlaceSnapshot place,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ViewerRole viewerRole,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) OwnerState ownerState,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Delivery delivery
) {
    public MemoryResponse {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(originKind, "originKind");
        Objects.requireNonNull(dataOrigin, "dataOrigin");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(atmospheres, "atmospheres");
        categories = List.copyOf(Objects.requireNonNull(categories, "categories"));
        if (categories.size() > PlaceCategoryCode.MAX_PER_MEMORY) {
            throw new IllegalArgumentException("too many categories");
        }
        Objects.requireNonNull(categoryStatus, "categoryStatus");
        CategoryStatus expectedCategoryStatus = categories.isEmpty()
                ? CategoryStatus.UNCLASSIFIED : CategoryStatus.CLASSIFIED;
        if (categoryStatus != expectedCategoryStatus) {
            throw new IllegalArgumentException("categoryStatus must describe final category slots");
        }
        Objects.requireNonNull(place, "place");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(viewerRole, "viewerRole");
        if (viewerRole == ViewerRole.OWNER && (ownerState == null || delivery != null)) {
            throw new IllegalArgumentException("owner projection requires ownerState only");
        }
        if (viewerRole == ViewerRole.RECIPIENT && (ownerState != null || delivery == null)) {
            throw new IllegalArgumentException("recipient projection requires delivery only");
        }
    }

    /** Final category-slot state, distinct from the historical analysis outcome. */
    public enum CategoryStatus {
        CLASSIFIED,
        UNCLASSIFIED
    }

    public enum ViewerRole {
        OWNER,
        RECIPIENT
    }

    public record PlaceSnapshot(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double lat,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double lng,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String label
    ) {
        public PlaceSnapshot {
            Objects.requireNonNull(id, "id");
        }
    }

    public record OwnerState(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ModerationStatus moderationStatus,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant availableAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") long likeCount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Analysis analysis
    ) {
        public OwnerState {
            Objects.requireNonNull(moderationStatus, "moderationStatus");
            if (likeCount < 0) {
                throw new IllegalArgumentException("likeCount must not be negative");
            }
            Objects.requireNonNull(analysis, "analysis");
        }
    }

    public record Analysis(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AtmosphereAnalysisStatus atmosphereStatus,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CategoryAnalysisStatus categoryStatus
    ) {
        public Analysis {
            Objects.requireNonNull(atmosphereStatus, "atmosphereStatus");
            Objects.requireNonNull(categoryStatus, "categoryStatus");
        }
    }

    public record Delivery(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID deliveryId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate serviceDate,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant deliveredAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant readAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant likedAt
    ) {
        public Delivery {
            Objects.requireNonNull(deliveryId, "deliveryId");
            Objects.requireNonNull(serviceDate, "serviceDate");
            Objects.requireNonNull(deliveredAt, "deliveredAt");
        }
    }
}
