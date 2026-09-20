package team4.emotionmap.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import team4.emotionmap.account.dto.OnboardingRequest;
import team4.emotionmap.account.dto.PreferencesRequest;
import team4.emotionmap.account.dto.UserResponse;
import team4.emotionmap.contracts.account.AccountStatus;
import team4.emotionmap.contracts.account.PreferenceVersionSnapshot;
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
    private static final GeoPoint INITIAL_MAILBOX = new GeoPoint(37.5, 127.0);
    private static final GeoPoint MOVED_MAILBOX = new GeoPoint(37.6, 127.1);
    private static final Instant ONBOARDED_AT = Instant.parse("2026-09-20T00:00:00Z");
    private static final Instant FIRST_EFFECTIVE_AT = Instant.parse("2026-09-20T00:01:00Z");
    private static final Instant MOVED_AT = Instant.parse("2026-09-20T00:01:30Z");
    private static final Instant LATEST_EFFECTIVE_AT = Instant.parse("2026-09-20T00:02:00Z");
    private static final Instant FIRST_CUTOFF = Instant.parse("2026-09-20T00:01:15Z");

    private static final ServiceConfigSource SERVICE_CONFIG = () -> new ServiceConfig("test", 1,
            new GeoPoint(1, 1), new ServiceLimits(3000, 1000, 1000, 5_000_000L, 6000, 6000, 20_000_000L,
            1800, 900, 20, 20, 50, 200), new AuthConfig(3600, 5, 60));

    private final UserRepository users = mock(UserRepository.class);
    private final EmailPasswordCredentialRepository credentials = mock(EmailPasswordCredentialRepository.class);
    private final UserPreferenceVersionRepository preferences = mock(UserPreferenceVersionRepository.class);
    private final SelectionPublicationBarrier publicationBarrier = mock(SelectionPublicationBarrier.class);
    private final UserService service = new UserService(users, credentials, preferences, mock(AccountAccessService.class),
            SERVICE_CONFIG, publicationBarrier);

    @Test
    void normalizedNumericNoOpPreservesTheCurrentPreferenceRowAndMailboxEpoch() {
        Atmospheres originalAxes = new Atmospheres(-1.0, 1.0, -1.0, 1.0);
        UserPreferenceVersion legacy = preference(1L, FIRST_EFFECTIVE_AT, originalAxes, "quiet places",
                LEGACY_AXIS_DEFINITION_VERSION, INITIAL_MAILBOX, ONBOARDED_AT);
        User user = stubLockedActiveUser();
        stubProfile();
        when(preferences.findFirstByUserIdOrderByRevisionDesc(USER_ID)).thenReturn(Optional.of(legacy));

        UserResponse response = service.updatePreferences(USER_ID,
                new PreferencesRequest(originalAxes, "  quiet places  ", "1", null, null));

        assertThat(response.preferenceVersion()).isEqualTo("1");
        assertThat(response.preferenceEffectiveAt()).isEqualTo(FIRST_EFFECTIVE_AT);
        assertThat(response.atmospheres()).isEqualTo(originalAxes);
        assertThat(legacy.getAxisDefinitionVersion()).isEqualTo(LEGACY_AXIS_DEFINITION_VERSION);
        assertThat(user.getMailboxEnabledAt()).isEqualTo(ONBOARDED_AT);
        verify(preferences, never()).save(any(UserPreferenceVersion.class));
        verify(publicationBarrier, never()).publicationTime();
    }

    @Test
    void staleExpectedRevisionIsRejectedBeforeAnOtherwiseValidMailboxMove() {
        Atmospheres originalAxes = new Atmospheres(-1.0, 1.0, -1.0, 1.0);
        UserPreferenceVersion legacy = preference(1L, FIRST_EFFECTIVE_AT, originalAxes, "quiet places",
                LEGACY_AXIS_DEFINITION_VERSION, INITIAL_MAILBOX, ONBOARDED_AT);
        User user = stubLockedActiveUser();
        when(preferences.findFirstByUserIdOrderByRevisionDesc(USER_ID)).thenReturn(Optional.of(legacy));

        ResponseStatusException error = catchThrowableOfType(ResponseStatusException.class,
                () -> service.updatePreferences(USER_ID,
                        new PreferencesRequest(originalAxes, "  quiet places  ", "2",
                                MOVED_MAILBOX.lat(), MOVED_MAILBOX.lng())));

        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(error.getReason()).isEqualTo("PREFERENCE_VERSION_CONFLICT");
        assertThat(user.mailbox()).isEqualTo(INITIAL_MAILBOX);
        assertThat(user.getMailboxEnabledAt()).isEqualTo(ONBOARDED_AT);
        verify(preferences, never()).save(any(UserPreferenceVersion.class));
        verify(credentials, never()).findById(any());
        verify(publicationBarrier, never()).publicationTime();
    }

    @Test
    void aSingleMailboxCoordinateIsRejectedWithoutARevision() {
        Atmospheres axes = new Atmospheres(-1.0, 1.0, -1.0, 1.0);
        UserPreferenceVersion current = preference(1L, FIRST_EFFECTIVE_AT, axes, "quiet places",
                LEGACY_AXIS_DEFINITION_VERSION, INITIAL_MAILBOX, ONBOARDED_AT);
        stubLockedActiveUser();
        when(preferences.findFirstByUserIdOrderByRevisionDesc(USER_ID)).thenReturn(Optional.of(current));

        ContractError error = catchThrowableOfType(ContractError.class,
                () -> service.updatePreferences(USER_ID,
                        new PreferencesRequest(axes, "quiet places", "1", INITIAL_MAILBOX.lat(), null)));

        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(error.fieldErrors()).containsExactly(FieldError.required("mailboxLng"));
        verify(preferences, never()).save(any(UserPreferenceVersion.class));
        verify(credentials, never()).findById(any());
        verify(publicationBarrier, never()).publicationTime();
    }

    @Test
    void sameMailboxAndPreferenceOnlyChangePreserveMailboxEpoch() {
        Atmospheres originalAxes = new Atmospheres(-1.0, 1.0, -1.0, 1.0);
        UserPreferenceVersion legacy = preference(1L, FIRST_EFFECTIVE_AT, originalAxes, "quiet places",
                LEGACY_AXIS_DEFINITION_VERSION, INITIAL_MAILBOX, ONBOARDED_AT);
        User user = stubLockedActiveUser();
        stubProfile();
        stubPublicationTime(LATEST_EFFECTIVE_AT);
        when(preferences.findFirstByUserIdOrderByRevisionDesc(USER_ID)).thenReturn(Optional.of(legacy));
        when(preferences.save(any(UserPreferenceVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, UserPreferenceVersion.class));

        UserResponse response = service.updatePreferences(USER_ID,
                new PreferencesRequest(originalAxes, "  now with friends  ", "1",
                        INITIAL_MAILBOX.lat(), INITIAL_MAILBOX.lng()));

        ArgumentCaptor<UserPreferenceVersion> saved = ArgumentCaptor.forClass(UserPreferenceVersion.class);
        verify(preferences).save(saved.capture());
        UserPreferenceVersion next = saved.getValue();
        assertThat(next.getRevision()).isEqualTo(2L);
        assertThat(next.getAxisDefinitionVersion()).isEqualTo(CURRENT_AXIS_DEFINITION_VERSION);
        assertThat(next.getDescription()).isEqualTo("now with friends");
        assertThat(new Atmospheres(next.getCrowdLevel(), next.getSpatialFeel(), next.getCompanyFit(),
                next.getStayStyle())).isEqualTo(originalAxes);
        assertThat(next.getMailboxLat()).isEqualTo(INITIAL_MAILBOX.lat());
        assertThat(next.getMailboxLng()).isEqualTo(INITIAL_MAILBOX.lng());
        assertThat(next.getMailboxEnabledAt()).isEqualTo(ONBOARDED_AT);
        assertThat(user.mailbox()).isEqualTo(INITIAL_MAILBOX);
        assertThat(user.getMailboxEnabledAt()).isEqualTo(ONBOARDED_AT);
        assertThat(response.preferenceVersion()).isEqualTo("2");
    }

    @Test
    void mailboxMoveAppendsFullSnapshotAndResetsEpochAtPublicationTime() {
        Atmospheres axes = new Atmospheres(-1.0, 1.0, -1.0, 1.0);
        UserPreferenceVersion current = preference(1L, FIRST_EFFECTIVE_AT, axes, "quiet places",
                LEGACY_AXIS_DEFINITION_VERSION, INITIAL_MAILBOX, ONBOARDED_AT);
        User user = stubLockedActiveUser();
        stubProfile();
        stubPublicationTime(MOVED_AT);
        when(preferences.findFirstByUserIdOrderByRevisionDesc(USER_ID)).thenReturn(Optional.of(current));
        when(preferences.save(any(UserPreferenceVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, UserPreferenceVersion.class));

        UserResponse response = service.updatePreferences(USER_ID,
                new PreferencesRequest(axes, "quiet places", "1", MOVED_MAILBOX.lat(), MOVED_MAILBOX.lng()));

        ArgumentCaptor<UserPreferenceVersion> saved = ArgumentCaptor.forClass(UserPreferenceVersion.class);
        verify(preferences).save(saved.capture());
        UserPreferenceVersion next = saved.getValue();
        assertThat(next.getRevision()).isEqualTo(2L);
        assertThat(next.getMailboxLat()).isEqualTo(MOVED_MAILBOX.lat());
        assertThat(next.getMailboxLng()).isEqualTo(MOVED_MAILBOX.lng());
        assertThat(next.getMailboxEnabledAt()).isEqualTo(MOVED_AT);
        assertThat(user.mailbox()).isEqualTo(MOVED_MAILBOX);
        assertThat(user.getMailboxEnabledAt()).isEqualTo(MOVED_AT);
        assertThat(response.mailbox().enabledAt()).isEqualTo(MOVED_AT);
        InOrder lockOrder = inOrder(publicationBarrier, users);
        lockOrder.verify(publicationBarrier).lock();
        lockOrder.verify(users).findByIdForUpdate(USER_ID);
    }

    @Test
    void onboardingAcceptsDescriptionAtConfiguredCodePointLimit() {
        String description = "😀".repeat(1000);
        Atmospheres axes = new Atmospheres(-1.0, 1.0, -1.0, 1.0);
        User user = stubLockedUnonboardedUser();
        stubProfile();
        stubPublicationTime(FIRST_EFFECTIVE_AT);
        when(preferences.save(any(UserPreferenceVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, UserPreferenceVersion.class));

        UserResponse response = service.completeOnboarding(USER_ID,
                new OnboardingRequest(INITIAL_MAILBOX.lat(), INITIAL_MAILBOX.lng(), axes, description));

        ArgumentCaptor<UserPreferenceVersion> saved = ArgumentCaptor.forClass(UserPreferenceVersion.class);
        verify(preferences).save(saved.capture());
        assertThat(saved.getValue().getDescription()).isEqualTo(description);
        assertThat(saved.getValue().getDescription().codePointCount(0, description.length())).isEqualTo(1000);
        assertThat(saved.getValue().getDescription().length()).isEqualTo(2000);
        assertThat(saved.getValue().getMailboxLat()).isEqualTo(INITIAL_MAILBOX.lat());
        assertThat(saved.getValue().getMailboxLng()).isEqualTo(INITIAL_MAILBOX.lng());
        assertThat(saved.getValue().getMailboxEnabledAt()).isEqualTo(FIRST_EFFECTIVE_AT);
        assertThat(user.mailbox()).isEqualTo(INITIAL_MAILBOX);
        assertThat(user.getMailboxEnabledAt()).isEqualTo(FIRST_EFFECTIVE_AT);
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
        verify(publicationBarrier, never()).publicationTime();
    }

    @Test
    void onboardingRetryUsesOriginalSnapshotAfterMailboxMoveAndReturnsLatestProfile() {
        Atmospheres originalAxes = new Atmospheres(-1.0, 1.0, -1.0, 1.0);
        Atmospheres changedAxes = new Atmospheres(-0.5, 0.0, 0.25, 1.0);
        UserPreferenceVersion first = preference(1L, FIRST_EFFECTIVE_AT, originalAxes, "quiet places",
                LEGACY_AXIS_DEFINITION_VERSION, INITIAL_MAILBOX, ONBOARDED_AT);
        UserPreferenceVersion latest = preference(2L, LATEST_EFFECTIVE_AT, changedAxes, "now with friends",
                CURRENT_AXIS_DEFINITION_VERSION, MOVED_MAILBOX, MOVED_AT);
        User user = stubLockedActiveUser(MOVED_MAILBOX, MOVED_AT);
        stubProfile();
        when(preferences.findByUserIdAndRevision(USER_ID, 1L)).thenReturn(Optional.of(first));
        when(preferences.findFirstByUserIdOrderByRevisionDesc(USER_ID)).thenReturn(Optional.of(latest));

        UserResponse response = service.completeOnboarding(USER_ID,
                new OnboardingRequest(INITIAL_MAILBOX.lat(), INITIAL_MAILBOX.lng(), originalAxes,
                        "  quiet places  "));

        assertThat(response.preferenceVersion()).isEqualTo("2");
        assertThat(response.preferenceEffectiveAt()).isEqualTo(LATEST_EFFECTIVE_AT);
        assertThat(response.atmospheres()).isEqualTo(changedAxes);
        assertThat(response.preferenceDescription()).isEqualTo("now with friends");
        assertThat(response.mailbox().lat()).isEqualTo(MOVED_MAILBOX.lat());
        assertThat(response.mailbox().lng()).isEqualTo(MOVED_MAILBOX.lng());
        assertThat(response.mailbox().enabledAt()).isEqualTo(MOVED_AT);
        assertThat(user.mailbox()).isEqualTo(MOVED_MAILBOX);
        verify(preferences, never()).save(any(UserPreferenceVersion.class));
        verify(publicationBarrier, never()).publicationTime();
    }

    @Test
    void historyAtCutoffMapsTheHistoricalMailboxAndEpoch() {
        Atmospheres axes = new Atmospheres(-1.0, 1.0, -1.0, 1.0);
        UserPreferenceVersion first = preference(1L, FIRST_EFFECTIVE_AT, axes, "quiet places",
                LEGACY_AXIS_DEFINITION_VERSION, INITIAL_MAILBOX, ONBOARDED_AT);
        when(preferences.findFirstByUserIdAndEffectiveAtLessThanEqualOrderByRevisionDesc(USER_ID, FIRST_CUTOFF))
                .thenReturn(Optional.of(first));

        PreferenceVersionSnapshot snapshot = new UserPreferenceHistoryReader(preferences)
                .findAt(USER_ID, FIRST_CUTOFF)
                .orElseThrow();

        assertThat(snapshot.preferenceVersion()).isEqualTo("1");
        assertThat(snapshot.mailbox()).isEqualTo(INITIAL_MAILBOX);
        assertThat(snapshot.mailboxEnabledAt()).isEqualTo(ONBOARDED_AT);
    }

    private void stubPublicationTime(Instant publicationTime) {
        when(publicationBarrier.publicationTime()).thenReturn(publicationTime);
    }

    private User stubLockedActiveUser() {
        return stubLockedActiveUser(INITIAL_MAILBOX, ONBOARDED_AT);
    }

    private User stubLockedActiveUser(GeoPoint mailbox, Instant mailboxEnabledAt) {
        User user = User.builder()
                .id(USER_ID)
                .accessStatus(AccountStatus.ACTIVE)
                .mailboxLat(mailbox.lat())
                .mailboxLng(mailbox.lng())
                .mailboxEnabledAt(mailboxEnabledAt)
                .updatedAt(mailboxEnabledAt)
                .build();
        when(users.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        return user;
    }

    private User stubLockedUnonboardedUser() {
        User user = User.builder()
                .id(USER_ID)
                .accessStatus(AccountStatus.ACTIVE)
                .updatedAt(ONBOARDED_AT)
                .build();
        when(users.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        return user;
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
                                                     String description, short axisDefinitionVersion, GeoPoint mailbox,
                                                     Instant mailboxEnabledAt) {
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
                .mailboxLat(mailbox.lat())
                .mailboxLng(mailbox.lng())
                .mailboxEnabledAt(mailboxEnabledAt)
                .build();
    }
}
