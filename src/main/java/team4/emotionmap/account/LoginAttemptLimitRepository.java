package team4.emotionmap.account;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Objects;
import org.springframework.stereotype.Repository;

/** Coordinates one durable login-attempt row with PostgreSQL row locks. */
@Repository
public class LoginAttemptLimitRepository {

    private final EntityManager entityManager;

    public LoginAttemptLimitRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** INSERT-on-conflict is intentionally followed by a pessimistic read of the same digest key. */
    public LoginAttemptLimit insertAndLock(String emailKeyHash, Instant initialUpdatedAt) {
        entityManager.createNativeQuery("""
                        insert into login_attempt_limits
                            (email_key_hash, window_started_at, failure_count, locked_until, updated_at)
                        values (:emailKeyHash, null, 0, null, :initialUpdatedAt)
                        on conflict (email_key_hash) do nothing
                        """)
                .setParameter("emailKeyHash", emailKeyHash)
                .setParameter("initialUpdatedAt", initialUpdatedAt)
                .executeUpdate();
        return entityManager.createQuery("""
                        select attempt
                        from LoginAttemptLimit attempt
                        where attempt.emailKeyHash = :emailKeyHash
                        """, LoginAttemptLimit.class)
                .setParameter("emailKeyHash", emailKeyHash)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getSingleResult();
    }

    /** PostgreSQL wall-clock time used for lock boundaries, never the application clock. */
    public Instant databaseNow() {
        Object result = entityManager.createNativeQuery("select clock_timestamp()").getSingleResult();
        return asInstant(result);
    }

    private static Instant asInstant(Object result) {
        Objects.requireNonNull(result, "database clock result");
        if (result instanceof Instant instant) {
            return instant;
        }
        if (result instanceof OffsetDateTime timestamp) {
            return timestamp.toInstant();
        }
        if (result instanceof ZonedDateTime timestamp) {
            return timestamp.toInstant();
        }
        if (result instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (result instanceof Date timestamp) {
            return timestamp.toInstant();
        }
        throw new IllegalStateException("Unsupported database clock type");
    }
}
