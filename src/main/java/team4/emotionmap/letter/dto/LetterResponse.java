package team4.emotionmap.letter.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import team4.emotionmap.letter.LetterAvailability;
import team4.emotionmap.letter.LetterDelivery;

public record LetterResponse(
        UUID deliveryId,
        UUID memoryId,
        LocalDate serviceDate,
        Instant deliveredAt,
        Instant readAt,
        Instant likedAt,
        LetterAvailability availability
) {
    public static LetterResponse from(LetterDelivery delivery, LetterAvailability availability) {
        return new LetterResponse(
                delivery.getId(), delivery.getMemoryId(), delivery.getServiceDate(),
                delivery.getDeliveredAt(), delivery.getReadAt(), delivery.getLikedAt(), availability);
    }
}
