package team4.emotionmap.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Durable limiter state keyed only by a domain-separated email digest. */
@Entity
@Table(name = "login_attempt_limits")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LoginAttemptLimit {

    @Id
    @Column(name = "email_key_hash", nullable = false, columnDefinition = "text")
    private String emailKeyHash;

    @Column(name = "window_started_at")
    private Instant windowStartedAt;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    boolean isLockedAt(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    boolean hasExpiredWindowAt(Instant now, int failedAttemptWindowSeconds) {
        return windowStartedAt != null && !now.isBefore(windowStartedAt.plusSeconds(failedAttemptWindowSeconds));
    }

    void reset(Instant now) {
        windowStartedAt = null;
        failureCount = 0;
        lockedUntil = null;
        updatedAt = now;
    }

    void recordFailure(Instant now, int failedLoginLimit, int loginLockSeconds) {
        if (failureCount == 0 || windowStartedAt == null) {
            failureCount = 1;
            windowStartedAt = now;
            lockedUntil = null;
        } else {
            failureCount++;
        }
        if (failureCount >= failedLoginLimit) {
            lockedUntil = now.plusSeconds(loginLockSeconds);
        }
        updatedAt = now;
    }
}
