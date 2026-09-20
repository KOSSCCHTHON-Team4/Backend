package team4.emotionmap.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import team4.emotionmap.account.dto.OnboardingRequest;
import team4.emotionmap.account.dto.PreferencesRequest;
import team4.emotionmap.account.dto.UserResponse;
import team4.emotionmap.contracts.account.AccountStatus;
import team4.emotionmap.contracts.config.AuthConfig;
import team4.emotionmap.contracts.config.ServiceConfig;
import team4.emotionmap.contracts.config.ServiceConfigSource;
import team4.emotionmap.contracts.config.ServiceLimits;
import team4.emotionmap.contracts.dictionary.Atmospheres;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.error.FieldError;
import team4.emotionmap.contracts.geo.GeoPoint;
import team4.emotionmap.contracts.time.SelectionPublicationBarrier;

class UserServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final short LEGACY_AXIS_DEFINITION_VERSION = 1;
    private static final short CURRENT_AXIS_DEFINITION_VERSION = 2;
    private static final Instant ONBOARDED_AT = Instant.parse("2026-09-20T00:00:00Z");
    private static final Instant FIRST_EFFECTIVE_AT = Instant.parse("2026-09-20T00:01:00Z");
    private static final Instant LATEST_EFFECTIVE_AT = Instant.parse("2026-09-20T00:02:00Z");

    private static final ServiceConfigSource SERVICE_CONFIG = () -> new ServiceConfig("test", 1,
            new GeoPoint(1, 1), new ServiceLimits(3000, 1000, 1000, 5_000_000L, 6000, 6000, 20_000_000L,
            1800, 900, 20, 20, 50, 200), new AuthConfig(3600, 5, 60));

    private final UserRepository users = mock(UserRepository.class);
    private final EmailPasswordCredentialRepository credentials = mock(EmailPasswordCredentialRepository.class);
    private final UserPreferenceVersionRepository preferences = mock(UserPreferenceVersionRepository.class);
    private final SelectionPublicationBarrier publicationBarrier = mock(SelectionPublicationBarrier.class);
    private final UserService service = new UserService(users, credentials, preferences, mock(AccountAccessService.class),
            SERVICE_CONFIG, publicationBarrier);

    @BeforeEach
    void publicationTime() {
        when(publicationBarrier.publicationTime()).thenReturn(LATEST_EFFECTIVE_AT);
    }

    @Test
    void normalizedNumericNoOpPreservesTheCurrentLegacyPreferenceRow() {
        Atmospheres originalAxes = new Atmospheres(-1.0, 1.0, -1.0, 1.0);
        UserPreferenceVersion legacy = preference(1L, FIRST_EFFECTIVE_AT, originalAxes, "quiet places",
                LEGACY_AXIS_DEFINITION_VERSION);
        stubLockedActiveUser();
        stubProfile();
        when(preferences.findFirstByUserIdOrderByRevisionDesc(USER_ID)).thenReturn(Optional.of(legacy));

        UserResponse response = service.updatePreferences(USER_ID,
                new PreferencesRequest(originalAxes, "  quiet places  ", "1"));

        assertThat(response.preferenceVersion()).isEqualTo("1");
        assertThat(response.preferenceEffectiveAt()).isEqualTo(FIRST_EFFECTIVE_AT);
        assertThat(response.atmospheres()).isEqualTo(originalAxes);
        assertThat(legacy.getAxisDefinitionVersion()).isEqualTo(LEGACY_AXIS_DEFINITION_VERSION);
        verify(preferences, never()).save(any(UserPreferenceVersion.class));
    }

    @Test
    void staleExpectedRevisionIsRejectedBeforeAnOtherwiseEquivalentNoOp() {
        Atmospheres originalAxes = new Atmospheres(-1.0, 1.0, -1.0, 1.0);
        UserPreferenceVersion legacy = preference(1L, FIRST_EFFECTIVE_AT, originalAxes, "quiet places",
                LEGACY_AXIS_DEFINITION_VERSION);
        stubLockedActiveUser();
        when(preferences.findFirstByUserIdOrderByRevisionDesc(USER_ID)).thenReturn(Optional.of(legacy));

        ResponseStatusException error = catchThrowableOfType(ResponseStatusException.class,
                () -> service.updatePreferences(USER_ID,
                        new PreferencesRequest(originalAxes, "  quiet places  ", "2")));

        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(error.getReason()).isEqualTo("PREFERENCE_VERSION_CONFLICT");
        verify(preferences, never()).save(any(UserPreferenceVersion.class));
        verify(credentials, never()).findById(any());
    }

    @Test
    void descriptionOnlyChangeAppendsAVersionTwoPreference() {
        Atmospheres originalAxes = new Atmospheres(-1.0, 1.0, -1.0, 1.0);
        UserPreferenceVersion legacy = preference(1L, FIRST_EFFECTIVE_AT, originalAxes, "quiet places",
                LEGACY_AXIS_DEFINITION_VERSION);
        stubLockedActiveUser();
        stubProfile();
        when(preferences.findFirstByUserIdOrderByRevisionDesc(USER_ID)).thenReturn(Optional.of(legacy));
        when(preferences.save(any(UserPreferenceVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, UserPreferenceVersion.class));

        UserResponse response = service.updatePreferences(USER_ID,
                new PreferencesRequest(originalAxes, "  now with friends  ", "1"));

        ArgumentCaptor<UserPreferenceVersion> saved = ArgumentCaptor.forClass(UserPreferenceVersion.class);
        verify(preferences).save(saved.capture());
        UserPreferenceVersion next = saved.getValue();
        assertThat(next.getRevision()).isEqualTo(2L);
        assertThat(next.getAxisDefinitionVersion()).isEqualTo(CURRENT_AXIS_DEFINITION_VERSION);
        assertThat(next.getDescription()).isEqualTo("now with friends");
        assertThat(new Atmospheres(next.getCrowdLevel(), next.getSpatialFeel(), next.getCompanyFit(),
                next.getStayStyle())).isEqualTo(originalAxes);
        assertThat(response.preferenceVersion()).isEqualTo("2");
    }

    @Test
    void onboardingAcceptsDescriptionAtConfiguredCodePointLimit() {
        String description = "😀".repeat(1000);
        Atmospheres axes = new Atmospheres(-1.0, 1.0, -1.0, 1.0);
        stubLockedUnonboardedUser();
        stubProfile();
        when(preferences.save(any(UserPreferenceVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, UserPreferenceVersion.class));

        UserResponse response = service.completeOnboarding(USER_ID,
                new OnboardingRequest(37.5, 127.0, axes, description));

        ArgumentCaptor<UserPreferenceVersion> saved = ArgumentCaptor.forClass(UserPreferenceVersion.class);
        verify(preferences).save(saved.capture());
        assertThat(saved.getValue().getDescription()).isEqualTo(description);
        assertThat(saved.getValue().getDescription().codePointCount(0, description.length())).isEqualTo(1000);
        assertThat(saved.getValue().getDescription().length()).isEqualTo(2000);
        assertThat(response.preferenceDescription()).isEqualTo(description);
    }

    @Test
    void onboardingRejectsDescriptionOverConfiguredCodePointLimitBeforePersisting() {
        String description = "😀".repeat(1001);
        stubLockedUnonboardedUser();

        ContractError error = catchThrowableOfType(ContractError.class,
                () -> service.completeOnboarding(USER_ID,
                        new OnboardingRequest(37.5, 127.0, new Atmospheres(-1.0, 1.0, -1.0, 1.0), description)));

        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(error.fieldErrors()).containsExactly(FieldError.tooLong("preferenceDescription"));
        verify(preferences, never()).save(any(UserPreferenceVersion.class));
        verify(credentials, never()).findById(any());
    }

    @Test
    void onboardingRetryComparesTheOriginalVersionAndReturnsTheLatestPreference() {
        Atmospheres originalAxes = new Atmospheres(-1.0, 1.0, -1.0, 1.0);
        Atmospheres changedAxes = new Atmospheres(-0.5, 0.0, 0.25, 1.0);
        UserPreferenceVersion first = preference(1L, FIRST_EFFECTIVE_AT, originalAxes, "quiet places",
                LEGACY_AXIS_DEFINITION_VERSION);
        UserPreferenceVersion latest = preference(2L, LATEST_EFFECTIVE_AT, changedAxes, "now with friends",
                CURRENT_AXIS_DEFINITION_VERSION);
        stubLockedActiveUser();
        stubProfile();
        when(preferences.findByUserIdAndRevision(USER_ID, 1L)).thenReturn(Optional.of(first));
        when(preferences.findFirstByUserIdOrderByRevisionDesc(USER_ID)).thenReturn(Optional.of(latest));

        UserResponse response = service.completeOnboarding(USER_ID,
                new OnboardingRequest(37.5, 127.0, originalAxes, "  quiet places  "));

        assertThat(response.preferenceVersion()).isEqualTo("2");
        assertThat(response.preferenceEffectiveAt()).isEqualTo(LATEST_EFFECTIVE_AT);
        assertThat(response.atmospheres()).isEqualTo(changedAxes);
        assertThat(response.preferenceDescription()).isEqualTo("now with friends");
        verify(preferences, never()).save(any(UserPreferenceVersion.class));
    }

    private void stubLockedActiveUser() {
        when(users.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(User.builder()
                .id(USER_ID)
                .accessStatus(AccountStatus.ACTIVE)
                .mailboxLat(37.5)
                .mailboxLng(127.0)
                .mailboxEnabledAt(ONBOARDED_AT)
                .updatedAt(ONBOARDED_AT)
                .build()));
    }

    private void stubLockedUnonboardedUser() {
        when(users.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(User.builder()
                .id(USER_ID)
                .accessStatus(AccountStatus.ACTIVE)
                .updatedAt(ONBOARDED_AT)
                .build()));
    }

    private void stubProfile() {
        when(credentials.findById(USER_ID)).thenReturn(Optional.of(EmailPasswordCredential.builder()
                .userId(USER_ID)
                .email("user@example.com")
                .emailLookupKey("user@example.com")
                .passwordHash("test-password-hash")
                .build()));
    }

    private static UserPreferenceVersion preference(long revision, Instant effectiveAt, Atmospheres axes,
                                                     String description, short axisDefinitionVersion) {
        return UserPreferenceVersion.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .revision(revision)
                .effectiveAt(effectiveAt)
                .crowdLevel(axes.crowdLevel())
                .spatialFeel(axes.spatialFeel())
                .companyFit(axes.companyFit())
                .stayStyle(axes.stayStyle())
                .description(description)
                .axisDefinitionVersion(axisDefinitionVersion)
                .build();
    }
}
