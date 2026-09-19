package team4.emotionmap.account;

import jakarta.validation.Validator;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import team4.emotionmap.account.dto.LoginRequest;
import team4.emotionmap.account.dto.LoginResponse;
import team4.emotionmap.contracts.config.AuthConfig;
import team4.emotionmap.contracts.config.ServiceConfigSource;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.error.FieldError;
import team4.emotionmap.platform.security.JwtTokenProvider;

@Service
public class AuthService {

    private static final SecureRandom DUMMY_PASSWORD_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final EmailPasswordCredentialRepository credentialRepository;
    private final UserPreferenceVersionRepository preferenceRepository;
    private final LoginAttemptLimitRepository attemptLimitRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final ServiceConfigSource serviceConfig;
    private final LoginPolicyProperties loginPolicyProperties;
    private final Validator validator;
    private final TransactionTemplate transactionTemplate;
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, EmailPasswordCredentialRepository credentialRepository,
                       UserPreferenceVersionRepository preferenceRepository,
                       LoginAttemptLimitRepository attemptLimitRepository, PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider, ServiceConfigSource serviceConfig,
                       LoginPolicyProperties loginPolicyProperties, Validator validator,
                       PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.preferenceRepository = preferenceRepository;
        this.attemptLimitRepository = attemptLimitRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.serviceConfig = serviceConfig;
        this.loginPolicyProperties = loginPolicyProperties;
        this.validator = validator;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.dummyPasswordHash = encodeDummyHash(passwordEncoder);
    }

    /**
     * The transaction returns a value rather than throwing an expected login result, so every counter mutation is
     * committed before its corresponding HTTP error is raised.
     */
    public LoginResponse login(LoginRequest request) {
        int failedAttemptWindowSeconds = loginPolicyProperties.requiredFailedAttemptWindowSeconds();
        AuthConfig authConfig = serviceConfig.current().auth();
        LoginInput input = requireLoginInput(request);
        LoginOutcome outcome = Objects.requireNonNull(transactionTemplate.execute(
                ignored -> loginInTransaction(input, authConfig, failedAttemptWindowSeconds)));

        if (outcome instanceof SuccessfulLogin successfulLogin) {
            JwtTokenProvider.IssuedToken token = tokenProvider.createToken(successfulLogin.userId());
            return new LoginResponse(token.token(), "Bearer", token.expiresAt(), token.expiresInSeconds(), false,
                    new LoginResponse.LoginUser(successfulLogin.userId(), successfulLogin.email(),
                            successfulLogin.hasOnboarded()));
        }
        if (outcome instanceof LockedLogin lockedLogin) {
            throw ContractError.retryAfter(ErrorCode.TOO_MANY_ATTEMPTS, lockedLogin.retryAfterSeconds());
        }
        if (outcome instanceof DeniedLogin deniedLogin) {
            throw ContractError.of(deniedLogin.errorCode());
        }
        if (outcome == InvalidCredentials.INSTANCE) {
            throw ContractError.of(ErrorCode.INVALID_CREDENTIALS);
        }
        throw new IllegalStateException("Unknown login outcome");
    }

    private LoginOutcome loginInTransaction(LoginInput input, AuthConfig authConfig, int failedAttemptWindowSeconds) {
        LoginAttemptLimit attemptLimit = attemptLimitRepository.insertAndLock(
                input.attemptKey(), attemptLimitRepository.databaseNow());
        Instant lockCheckNow = attemptLimitRepository.databaseNow();
        if (attemptLimit.isLockedAt(lockCheckNow)) {
            return new LockedLogin(retryAfterSeconds(lockCheckNow, attemptLimit.getLockedUntil()));
        }

        EmailPasswordCredential observedCredential = credentialRepository
                .findByEmailLookupKey(input.normalizedEmail())
                .orElse(null);
        User user = null;
        EmailPasswordCredential lockedCredential = null;
        if (observedCredential != null) {
            // The account row is always acquired before its credential row.
            user = userRepository.findByIdForUpdate(observedCredential.getUserId()).orElse(null);
            lockedCredential = credentialRepository.findByEmailLookupKeyForUpdate(input.normalizedEmail()).orElse(null);
        }

        boolean passwordMatches;
        if (user == null || lockedCredential == null
                || !lockedCredential.getUserId().equals(user.getId())) {
            passwordEncoder.matches(input.password(), dummyPasswordHash);
            passwordMatches = false;
        } else {
            passwordMatches = passwordEncoder.matches(input.password(), lockedCredential.getPasswordHash());
        }

        Instant now = attemptLimitRepository.databaseNow();
        if (attemptLimit.getLockedUntil() != null && !now.isBefore(attemptLimit.getLockedUntil())) {
            attemptLimit.reset(now);
        }
        if (attemptLimit.hasExpiredWindowAt(now, failedAttemptWindowSeconds)) {
            attemptLimit.reset(now);
        }

        if (!passwordMatches) {
            attemptLimit.recordFailure(now, authConfig.failedLoginLimit(), authConfig.loginLockSeconds());
            if (attemptLimit.getLockedUntil() != null) {
                return new LockedLogin(retryAfterSeconds(now, attemptLimit.getLockedUntil()));
            }
            return InvalidCredentials.INSTANCE;
        }

        attemptLimit.reset(now);
        ErrorCode denialCode = user.getAccessStatus().denialCode();
        if (denialCode != null) {
            return new DeniedLogin(denialCode);
        }
        boolean hasOnboarded = user.getMailboxEnabledAt() != null
                && preferenceRepository.findByUserIdAndRevision(user.getId(), 1L).isPresent();
        return new SuccessfulLogin(user.getId(), lockedCredential.getEmail(), hasOnboarded);
    }

    private LoginInput requireLoginInput(LoginRequest request) {
        if (request == null) {
            throw ContractError.of(ErrorCode.VALIDATION_ERROR, FieldError.required("request"));
        }

        String normalizedEmail;
        try {
            normalizedEmail = EmailPasswordInput.normalizeEmail(request.email(), validator);
        } catch (IllegalArgumentException e) {
            throw ContractError.of(ErrorCode.VALIDATION_ERROR,
                    request.email() == null ? FieldError.required("email") : FieldError.invalid("email"));
        }
        if (request.password() == null || request.password().isEmpty()) {
            throw ContractError.of(ErrorCode.VALIDATION_ERROR, FieldError.required("password"));
        }
        if (!EmailPasswordInput.hasValidPassword(request.password())) {
            throw ContractError.of(ErrorCode.VALIDATION_ERROR, FieldError.invalid("password"));
        }
        return new LoginInput(normalizedEmail, request.password(), EmailPasswordInput.loginAttemptKey(normalizedEmail));
    }

    private static String encodeDummyHash(PasswordEncoder passwordEncoder) {
        byte[] randomBytes = new byte[32];
        DUMMY_PASSWORD_RANDOM.nextBytes(randomBytes);
        try {
            String randomPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
            return passwordEncoder.encode(randomPassword);
        } finally {
            Arrays.fill(randomBytes, (byte) 0);
        }
    }

    private static int retryAfterSeconds(Instant now, Instant lockedUntil) {
        Duration remaining = Duration.between(now, lockedUntil);
        long seconds = remaining.getSeconds();
        if (remaining.getNano() > 0) {
            seconds++;
        }
        if (seconds < 1) {
            return 1;
        }
        return seconds > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) seconds;
    }

    private record LoginInput(String normalizedEmail, String password, String attemptKey) {
    }

    private interface LoginOutcome {
    }

    private record SuccessfulLogin(UUID userId, String email, boolean hasOnboarded) implements LoginOutcome {
    }

    private record LockedLogin(int retryAfterSeconds) implements LoginOutcome {
    }

    private record DeniedLogin(ErrorCode errorCode) implements LoginOutcome {
    }

    private enum InvalidCredentials implements LoginOutcome {
        INSTANCE
    }
}
