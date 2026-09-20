package team4.emotionmap.letter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LetterLikeResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID deliveryId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Status status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant likedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID copiedMemoryId
) {
    public LetterLikeResponse {
        Objects.requireNonNull(deliveryId, "deliveryId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(likedAt, "likedAt");
        if ((status == Status.COPIED) != (copiedMemoryId != null)) {
            throw new IllegalArgumentException("copiedMemoryId is present only for an initial copy");
        }
    }

    public enum Status {
        COPIED,
        ALREADY_COPIED
    }
}
