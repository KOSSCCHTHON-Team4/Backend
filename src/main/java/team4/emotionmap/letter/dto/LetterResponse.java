package team4.emotionmap.letter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import team4.emotionmap.contracts.dictionary.Atmospheres;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;
import team4.emotionmap.contracts.memory.DataOrigin;
import team4.emotionmap.letter.LetterAvailability;
import team4.emotionmap.memory.dto.MemoryResponse;

/**
 * Receiver-safe delivery card. Unavailable cards retain only delivery history and memoryId.
 */
public record LetterResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID deliveryId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID memoryId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate serviceDate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant deliveredAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant readAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant likedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LetterAvailability availability,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UnavailableReason unavailableReason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String content,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String imageUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Atmospheres atmospheres,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PlaceCategoryCode> categories,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) MemoryResponse.CategoryStatus categoryStatus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) MemoryResponse.PlaceSnapshot place,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) DataOrigin dataOrigin
) {
    public LetterResponse {
        Objects.requireNonNull(deliveryId, "deliveryId");
        Objects.requireNonNull(memoryId, "memoryId");
        Objects.requireNonNull(serviceDate, "serviceDate");
        Objects.requireNonNull(deliveredAt, "deliveredAt");
        Objects.requireNonNull(availability, "availability");
        Objects.requireNonNull(categories, "categories");
        categories = List.copyOf(categories);
        if (availability == LetterAvailability.AVAILABLE) {
            if (unavailableReason != null || content == null || atmospheres == null || categoryStatus == null
                    || place == null || createdAt == null || dataOrigin == null) {
                throw new IllegalArgumentException("available delivery requires the full source card");
            }
        } else if (unavailableReason == null || content != null || imageUrl != null || atmospheres != null
                || !categories.isEmpty() || categoryStatus != null || place != null || createdAt != null
                || dataOrigin != null) {
            throw new IllegalArgumentException("unavailable delivery must be an explicit tombstone");
        }
    }

    public enum UnavailableReason {
        DELETED,
        HIDDEN
    }
}
