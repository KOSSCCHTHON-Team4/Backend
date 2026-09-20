package team4.emotionmap.letter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Result of marking a delivery read; source content is deliberately not repeated. */
public record LetterReadResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID deliveryId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant readAt
) {
    public LetterReadResponse {
        Objects.requireNonNull(deliveryId, "deliveryId");
        Objects.requireNonNull(readAt, "readAt");
    }
}
