package team4.emotionmap.notification.dto;

import java.time.Instant;
import java.util.UUID;
import team4.emotionmap.notification.Notification;

/** 알림 응답. 원작성자 정보는 넣지 않는다(익명). */
public record NotificationResponse(UUID id, UUID preferenceId, UUID placeId, UUID memoryId,
                                   double similarity, double score, int stage, String reason,
                                   Instant createdAt, Instant readAt) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getPreferenceId(), n.getPlaceId(), n.getMemoryId(),
                n.getSimilarity(), n.getScore(), n.getStage(), n.getReason(), n.getCreatedAt(), n.getReadAt());
    }
}
