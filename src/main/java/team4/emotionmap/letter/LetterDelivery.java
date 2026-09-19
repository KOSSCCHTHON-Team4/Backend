package team4.emotionmap.letter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * letter_delivery 테이블 매핑 (V1__init.sql).
 * Memory 를 수신자에게 편지로 배달. score = 추천 점수(유사도 등).
 * readAt 은 아직 안 읽었으면 null.
 */
@Entity
@Table(name = "letter_delivery")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LetterDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    @Column(name = "memory_id", nullable = false)
    private Long memoryId;

    @Column(nullable = false)
    private Double score;

    @Column(nullable = false)
    private OffsetDateTime deliveredAt;

    private OffsetDateTime readAt;

    @Builder
    private LetterDelivery(Long receiverId, Long memoryId, Double score) {
        this.receiverId = receiverId;
        this.memoryId = memoryId;
        this.score = score;
        this.deliveredAt = OffsetDateTime.now();
    }

    /** 수신자가 편지를 읽은 시각을 기록. */
    public void markRead() {
        this.readAt = OffsetDateTime.now();
    }
}
