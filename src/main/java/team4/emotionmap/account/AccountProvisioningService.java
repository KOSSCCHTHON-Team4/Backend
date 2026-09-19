package team4.emotionmap.account;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import jakarta.validation.Validator;
import java.nio.CharBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import team4.emotionmap.contracts.account.AccountStatus;

/** Narrow internal service used only by the explicit provisioning CLI bootstrap. */
public class AccountProvisioningService {

    private final EntityManager entityManager;
    private final LoginAttemptLimitRepository attemptLimitRepository;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;
    private final TransactionTemplate transactionTemplate;

    public AccountProvisioningService(EntityManager entityManager, LoginAttemptLimitRepository attemptLimitRepository,
                                      PasswordEncoder passwordEncoder, Validator validator,
                                      PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.attemptLimitRepository = attemptLimitRepository;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Creates exactly one USER account and credential. The caller transfers ownership of {@code rawPassword}, which is
     * cleared before this method returns or throws.
     */
    public ProvisionedAccount provision(String email, char[] rawPassword, AccountStatus accessStatus) {
        try {
            String normalizedEmail = EmailPasswordInput.normalizeEmail(email, validator);
            requireInitialStatus(accessStatus);
            if (!EmailPasswordInput.hasValidPassword(CharBuffer.wrap(Objects.requireNonNull(rawPassword)))) {
                throw new IllegalArgumentException("initial password is invalid");
            }
            String passwordHash = passwordEncoder.encode(CharBuffer.wrap(rawPassword));
            if (passwordHash == null || passwordHash.isBlank()) {
                throw new IllegalStateException("password encoder returned an invalid hash");
            }
            String attemptKey = EmailPasswordInput.loginAttemptKey(normalizedEmail);
            return Objects.requireNonNull(transactionTemplate.execute(
                    ignored -> provisionInTransaction(normalizedEmail, attemptKey, passwordHash, accessStatus)));
        } finally {
            if (rawPassword != null) {
                Arrays.fill(rawPassword, '\0');
            }
        }
    }

    private ProvisionedAccount provisionInTransaction(String normalizedEmail, String attemptKey, String passwordHash,
                                                       AccountStatus accessStatus) {
        attemptLimitRepository.insertAndLock(attemptKey, attemptLimitRepository.databaseNow());

        EmailPasswordCredential observedCredential = findCredential(normalizedEmail, null);
        if (observedCredential != null) {
            // Keep the same user-then-credential lock order as login before rejecting a duplicate.
            entityManager.find(User.class, observedCredential.getUserId(), LockModeType.PESSIMISTIC_WRITE);
            if (findCredential(normalizedEmail, LockModeType.PESSIMISTIC_WRITE) != null) {
                throw new DuplicateEmailException();
            }
        }

        Instant now = attemptLimitRepository.databaseNow();
        User user = User.builder()
                .accessStatus(accessStatus)
                .appRole(AppRole.USER)
                .createdAt(now)
                .updatedAt(now)
                .build();
        entityManager.persist(user);
        entityManager.flush();
        UUID userId = Objects.requireNonNull(user.getId(), "generated user id");

        EmailPasswordCredential credential = EmailPasswordCredential.builder()
                .userId(userId)
                .email(normalizedEmail)
                .emailLookupKey(normalizedEmail)
                .passwordHash(passwordHash)
                .passwordChangedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        entityManager.persist(credential);
        return new ProvisionedAccount(userId, accessStatus);
    }

    private EmailPasswordCredential findCredential(String normalizedEmail, LockModeType lockMode) {
        TypedQuery<EmailPasswordCredential> query = entityManager.createQuery("""
                        select credential
                        from EmailPasswordCredential credential
                        where credential.emailLookupKey = :emailLookupKey
                        """, EmailPasswordCredential.class)
                .setParameter("emailLookupKey", normalizedEmail);
        if (lockMode != null) {
            query.setLockMode(lockMode);
        }
        return query.getResultList().stream().findFirst().orElse(null);
    }

    private static void requireInitialStatus(AccountStatus accessStatus) {
        if (accessStatus != AccountStatus.ACTIVE && accessStatus != AccountStatus.PENDING) {
            throw new IllegalArgumentException("initial access status is invalid");
        }
    }

    public record ProvisionedAccount(UUID userId, AccountStatus accessStatus) {
    }

    static final class DuplicateEmailException extends RuntimeException {
        private DuplicateEmailException() {
            super("email already provisioned");
        }
    }
}
