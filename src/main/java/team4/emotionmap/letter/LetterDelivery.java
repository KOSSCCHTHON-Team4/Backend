package team4.emotionmap.letter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "letter_deliveries", uniqueConstraints = {
        @UniqueConstraint(name = "uq_letter_deliveries_receiver_date", columnNames = {"receiver_id", "service_date"}),
        @UniqueConstraint(name = "uq_letter_deliveries_receiver_memory", columnNames = {"receiver_id", "memory_id"})
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LetterDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "receiver_id", nullable = false, updatable = false)
    private UUID receiverId;

    @Column(name = "service_date", nullable = false, updatable = false)
    private LocalDate serviceDate;

    @Column(name = "memory_id", nullable = false, updatable = false)
    private UUID memoryId;

    @Builder.Default
    @Column(name = "delivered_at", nullable = false, updatable = false)
    private Instant deliveredAt = Instant.now();

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "liked_at")
    private Instant likedAt;

    /** Called while holding the delivery row lock. */
    public void markRead(Instant at) {
        if (readAt == null) {
            readAt = at.truncatedTo(ChronoUnit.MICROS);
        }
    }

    /** Called in the same locked transaction as the independent PRIVATE insert. */
    public void markLiked(Instant at) {
        if (likedAt != null) {
            throw new IllegalStateException("Letter was already copied");
        }
        likedAt = at.truncatedTo(ChronoUnit.MICROS);
    }
}
