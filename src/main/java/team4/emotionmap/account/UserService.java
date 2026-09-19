package team4.emotionmap.account;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import team4.emotionmap.account.dto.Atmospheres;
import team4.emotionmap.account.dto.OnboardingRequest;
import team4.emotionmap.account.dto.PreferencesRequest;
import team4.emotionmap.account.dto.UserResponse;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final EmailPasswordCredentialRepository credentialRepository;
    private final UserPreferenceVersionRepository preferenceRepository;
    private final AccountAccessService accountAccessService;

    @Transactional(readOnly = true)
    public UserResponse getMe(UUID userId) {
        User user = accountAccessService.requireActive(userId);
        return profile(user, preferenceRepository.findFirstByUserIdOrderByRevisionDesc(userId).orElse(null));
    }

    @Transactional
    public UserResponse completeOnboarding(UUID userId, OnboardingRequest request) {
        User user = lockedActiveUser(userId);
        String description = normalizeDescription(request.preferenceDescription());
        if (user.getMailboxEnabledAt() != null) {
            UserPreferenceVersion first = preferenceRepository.findByUserIdAndRevision(userId, 1L)
                    .orElseThrow(() -> new IllegalStateException("Onboarded account has no first preference version"));
            if (!Objects.equals(user.getMailboxLat(), request.mailboxLat())
                    || !Objects.equals(user.getMailboxLng(), request.mailboxLng())
                    || !samePreference(first, request.atmospheres(), description)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "ONBOARDING_ALREADY_COMPLETED");
            }
            return profile(user, latestPreference(userId));
        }
        Instant now = Instant.now();
        UserPreferenceVersion first = preferenceRepository.save(newPreference(
                userId, 1L, now, request.atmospheres(), description));
        user.completeOnboarding(request.mailboxLat(), request.mailboxLng(), now);
        return profile(user, first);
    }

    @Transactional
    public UserResponse updatePreferences(UUID userId, PreferencesRequest request) {
        User user = lockedActiveUser(userId);
        if (user.getMailboxEnabledAt() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ONBOARDING_REQUIRED");
        }
        UserPreferenceVersion current = latestPreference(userId);
        if (!current.getRevision().toString().equals(request.expectedPreferenceVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PREFERENCE_VERSION_CONFLICT");
        }
        String description = normalizeDescription(request.preferenceDescription());
        if (samePreference(current, request.atmospheres(), description)) {
            return profile(user, current);
        }
        Instant changedAt = Instant.now();
        UserPreferenceVersion next = preferenceRepository.save(newPreference(userId,
                Math.addExact(current.getRevision(), 1L), changedAt, request.atmospheres(), description));
        user.recordPreferenceChange(changedAt);
        return profile(user, next);
    }

    private User lockedActiveUser(UUID userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN"));
        AccountAccessService.requireActive(user);
        return user;
    }

    private UserPreferenceVersion latestPreference(UUID userId) {
        return preferenceRepository.findFirstByUserIdOrderByRevisionDesc(userId)
                .orElseThrow(() -> new IllegalStateException("Onboarded account has no preference version"));
    }

    private UserResponse profile(User user, UserPreferenceVersion preference) {
        String email = credentialRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalStateException("Account has no email-password credential"))
                .getEmail();
        return UserResponse.from(user, email, preference);
    }

    private static String normalizeDescription(String description) {
        return description == null || description.isBlank() ? null : description.strip();
    }

    private static boolean samePreference(UserPreferenceVersion version, Atmospheres axes, String description) {
        return Atmospheres.from(version).equals(axes) && Objects.equals(version.getDescription(), description);
    }

    private static UserPreferenceVersion newPreference(UUID userId, long revision, Instant effectiveAt,
                                                       Atmospheres axes, String description) {
        return UserPreferenceVersion.builder()
                .userId(userId).revision(revision).effectiveAt(effectiveAt)
                .crowdLevel(axes.crowdLevel()).spatialFeel(axes.spatialFeel())
                .companyFit(axes.companyFit()).stayStyle(axes.stayStyle())
                .description(description).build();
    }
}
