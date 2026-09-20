package team4.emotionmap.account;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import team4.emotionmap.account.dto.OnboardingRequest;
import team4.emotionmap.account.dto.PreferencesRequest;
import team4.emotionmap.account.dto.UserResponse;
import team4.emotionmap.contracts.config.ServiceConfigSource;
import team4.emotionmap.contracts.dictionary.Atmospheres;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.error.FieldError;
import team4.emotionmap.contracts.geo.GeoPoint;
import team4.emotionmap.contracts.time.SelectionPublicationBarrier;
import team4.emotionmap.contracts.validation.TextRules;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final EmailPasswordCredentialRepository credentialRepository;
    private final UserPreferenceVersionRepository preferenceRepository;
    private final AccountAccessService accountAccessService;
    private final ServiceConfigSource serviceConfig;
    private final SelectionPublicationBarrier publicationBarrier;

    @Transactional(readOnly = true)
    public UserResponse getMe(UUID userId) {
        User user = accountAccessService.requireActive(userId);
        return profile(user, preferenceRepository.findFirstByUserIdOrderByRevisionDesc(userId).orElse(null));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserResponse completeOnboarding(UUID userId, OnboardingRequest request) {
        publicationBarrier.lock();
        User user = lockedActiveUser(userId);
        String description = normalizeDescription(request.preferenceDescription());
        GeoPoint requestedMailbox = validMailbox(request.mailboxLat(), request.mailboxLng());
        if (user.getMailboxEnabledAt() != null) {
            UserPreferenceVersion first = preferenceRepository.findByUserIdAndRevision(userId, 1L)
                    .orElseThrow(() -> new IllegalStateException("Onboarded account has no first preference version"));
            if (!sameMailbox(first, requestedMailbox)
                    || !samePreference(first, request.atmospheres(), description)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "ONBOARDING_ALREADY_COMPLETED");
            }
            return profile(user, latestPreference(userId));
        }

        Instant publicationTime = publicationBarrier.publicationTime();
        user.completeOnboarding(requestedMailbox, publicationTime);
        UserPreferenceVersion first = preferenceRepository.save(newPreference(
                userId,
                1L,
                publicationTime,
                request.atmospheres(),
                description,
                user.mailbox(),
                user.getMailboxEnabledAt()));
        return profile(user, first);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserResponse updatePreferences(UUID userId, PreferencesRequest request) {
        publicationBarrier.lock();
        User user = lockedActiveUser(userId);
        if (user.getMailboxEnabledAt() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ONBOARDING_REQUIRED");
        }

        UserPreferenceVersion current = latestPreference(userId);
        if (!current.getRevision().toString().equals(request.expectedPreferenceVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PREFERENCE_VERSION_CONFLICT");
        }

        String description = normalizeDescription(request.preferenceDescription());
        GeoPoint requestedMailbox = requestedMailbox(user, request);
        boolean preferenceChanged = !samePreference(current, request.atmospheres(), description);
        boolean mailboxChanged = !user.mailbox().equals(requestedMailbox);
        if (!preferenceChanged && !mailboxChanged) {
            return profile(user, current);
        }

        Instant publicationTime = publicationBarrier.publicationTime();
        if (mailboxChanged) {
            user.relocateMailbox(requestedMailbox, publicationTime);
        } else {
            user.recordPreferenceChange(publicationTime);
        }
        UserPreferenceVersion next = preferenceRepository.save(newPreference(
                userId,
                Math.addExact(current.getRevision(), 1L),
                publicationTime,
                request.atmospheres(),
                description,
                user.mailbox(),
                user.getMailboxEnabledAt()));
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

    private String normalizeDescription(String description) {
        return TextRules.normalizeOptionalText(description,
                serviceConfig.limits().preferenceDescriptionMaxCodePoints(),
                "preferenceDescription", ErrorCode.VALIDATION_ERROR);
    }

    private static GeoPoint requestedMailbox(User user, PreferencesRequest request) {
        if (!request.hasPairedMailboxCoordinates()) {
            if (request.mailboxLat() == null) {
                throw ContractError.of(ErrorCode.VALIDATION_ERROR, FieldError.required("mailboxLat"));
            }
            throw ContractError.of(ErrorCode.VALIDATION_ERROR, FieldError.required("mailboxLng"));
        }
        if (request.mailboxLat() == null) {
            return user.mailbox();
        }
        return validMailbox(request.mailboxLat(), request.mailboxLng());
    }

    private static GeoPoint validMailbox(Double mailboxLat, Double mailboxLng) {
        if (mailboxLat == null || mailboxLng == null) {
            if (mailboxLat == null && mailboxLng == null) {
                throw ContractError.of(ErrorCode.VALIDATION_ERROR,
                        FieldError.required("mailboxLat"), FieldError.required("mailboxLng"));
            }
            throw ContractError.of(ErrorCode.VALIDATION_ERROR,
                    FieldError.required(mailboxLat == null ? "mailboxLat" : "mailboxLng"));
        }
        boolean validLat = GeoPoint.isValidLat(mailboxLat);
        boolean validLng = GeoPoint.isValidLng(mailboxLng);
        if (!validLat || !validLng) {
            if (!validLat && !validLng) {
                throw ContractError.of(ErrorCode.VALIDATION_ERROR,
                        FieldError.outOfRange("mailboxLat"), FieldError.outOfRange("mailboxLng"));
            }
            throw ContractError.of(ErrorCode.VALIDATION_ERROR,
                    FieldError.outOfRange(validLat ? "mailboxLng" : "mailboxLat"));
        }
        return User.normalizedMailbox(mailboxLat, mailboxLng);
    }

    private static boolean sameMailbox(UserPreferenceVersion version, GeoPoint mailbox) {
        return User.normalizedMailbox(version.getMailboxLat(), version.getMailboxLng()).equals(mailbox);
    }

    private static boolean samePreference(UserPreferenceVersion version, Atmospheres axes, String description) {
        return new Atmospheres(version.getCrowdLevel(), version.getSpatialFeel(),
                version.getCompanyFit(), version.getStayStyle()).equals(axes)
                && Objects.equals(version.getDescription(), description);
    }

    private static UserPreferenceVersion newPreference(UUID userId, long revision, Instant effectiveAt,
                                                       Atmospheres axes, String description, GeoPoint mailbox,
                                                       Instant mailboxEnabledAt) {
        return UserPreferenceVersion.builder()
                .userId(userId)
                .revision(revision)
                .effectiveAt(effectiveAt)
                .crowdLevel(axes.crowdLevel())
                .spatialFeel(axes.spatialFeel())
                .companyFit(axes.companyFit())
                .stayStyle(axes.stayStyle())
                .description(description)
                .mailboxLat(mailbox.lat())
                .mailboxLng(mailbox.lng())
                .mailboxEnabledAt(mailboxEnabledAt)
                .build();
    }
}
