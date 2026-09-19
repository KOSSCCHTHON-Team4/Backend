package team4.emotionmap.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 매칭 통과 알림(기획 §9 Notification). 같은 취향·같은 장소는 KST 하루 1회(UNIQUE). */
@Entity
@Table(name = "notifications", uniqueConstraints =
        @UniqueConstraint(name = "uq_notification_daily", columnNames = {"preference_id", "place_id", "service_date"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "preference_id", nullable = false, updatable = false)
    private UUID preferenceId;

    @Column(name = "place_id", nullable = false, updatable = false)
    private UUID placeId;

    @Column(name = "memory_id", nullable = false, updatable = false)
    private UUID memoryId;

    @Column(name = "service_date", nullable = false, updatable = false)
    private LocalDate serviceDate;

    @Column(nullable = false, updatable = false)
    private Double similarity;

    @Column(nullable = false, updatable = false)
    private Double score;

    @Column(nullable = false, updatable = false)
    private Short stage;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String reason;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "read_at")
    private Instant readAt;

    public void markRead(Instant at) {
        if (readAt == null) {
            readAt = at;
        }
    }
}
