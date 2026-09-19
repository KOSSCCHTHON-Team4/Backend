package team4.emotionmap.letter.dto;

import java.time.Instant;
import java.util.UUID;

public record LetterLikeResponse(
        UUID deliveryId,
        Status status,
        Instant likedAt,
        UUID copiedMemoryId
) {
    public enum Status {
        COPIED,
        ALREADY_COPIED
    }
}
