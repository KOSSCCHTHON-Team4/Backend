package team4.emotionmap.letter.dto;

import java.time.OffsetDateTime;
import team4.emotionmap.letter.LetterDelivery;

/**
 * 편지(추천 배달) 응답 DTO. readAt 이 null 이면 아직 안 읽음.
 */
public record LetterResponse(
        Long id,
        Long receiverId,
        Long memoryId,
        Double score,
        OffsetDateTime deliveredAt,
        OffsetDateTime readAt
) {
    public static LetterResponse from(LetterDelivery l) {
        return new LetterResponse(
                l.getId(), l.getReceiverId(), l.getMemoryId(),
                l.getScore(), l.getDeliveredAt(), l.getReadAt());
    }
}
