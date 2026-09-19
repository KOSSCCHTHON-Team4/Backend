package team4.emotionmap.memory.reaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * reaction 테이블 매핑 (V1__init.sql).
 * 한 사용자가 같은 Memory 에 한 번만 반응할 수 있다 (user_id, memory_id) UNIQUE.
 */
@Entity
@Table(name = "reaction",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_reaction_user_memory",
                columnNames = {"user_id", "memory_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "memory_id", nullable = false)
    private Long memoryId;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Builder
    private Reaction(Long userId, Long memoryId) {
        this.userId = userId;
        this.memoryId = memoryId;
        this.createdAt = OffsetDateTime.now();
    }
}
