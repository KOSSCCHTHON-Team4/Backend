package team4.emotionmap.account;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import team4.emotionmap.platform.security.AccountAccessGuard;

@Service
@RequiredArgsConstructor
public class AccountAccessService implements AccountAccessGuard {

    private final UserRepository userRepository;
    private final UserPreferenceVersionRepository preferenceRepository;

    @Override
    @Transactional(readOnly = true)
    public String requireAccess(UUID userId, String requestPath) {
        User user = requireActive(userId);
        boolean beforeOnboardingAllowed = switch (requestPath) {
            case "/v1/config", "/v1/atmosphere-axes", "/v1/place-categories",
                    "/v1/users/me", "/v1/users/me/onboarding" -> true;
            default -> false;
        };
        if (!beforeOnboardingAllowed && (user.getMailboxEnabledAt() == null
                || preferenceRepository.findByUserIdAndRevision(userId, 1L).isEmpty())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ONBOARDING_REQUIRED");
        }
        return "ROLE_" + user.getAppRole().name();
    }

    @Transactional(readOnly = true)
    public User requireActive(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN"));
        requireActive(user);
        return user;
    }

    public static void requireActive(User user) {
        switch (user.getAccessStatus()) {
            case ACTIVE -> { }
            case PENDING -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "INVITATION_REQUIRED");
            case SUSPENDED -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED");
            case CLOSED -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ACCOUNT_CLOSED");
        }
    }
}
