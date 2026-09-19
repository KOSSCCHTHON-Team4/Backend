package team4.emotionmap.letter;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "daily_selections")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DailySelection {

    @EmbeddedId
    private DailySelectionId id;

    @Column(name = "cutoff_at", nullable = false)
    private Instant cutoffAt;

    @Column(name = "preference_version_id", nullable = false)
    private UUID preferenceVersionId;

    @Column(name = "radius_m", nullable = false)
    private Integer radiusM;

    @Builder.Default
    @Column(name = "rule_version", nullable = false, columnDefinition = "text")
    private String ruleVersion = "atmosphere-v1";

    @Builder.Default
    @Column(name = "random_seed", nullable = false)
    private UUID randomSeed = UUID.randomUUID();

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private DailySelectionStatus status = DailySelectionStatus.PENDING;

    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "claim_token")
    private UUID claimToken;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "last_error_code", columnDefinition = "text")
    private String lastErrorCode;

    @Column(name = "candidate_count")
    private Integer candidateCount;

    @Column(name = "top_tie_count")
    private Integer topTieCount;

    @Column(name = "fixed_score")
    private Short fixedScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "tie_break_method", columnDefinition = "text")
    private TieBreakMethod tieBreakMethod;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
