package team4.emotionmap.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import team4.emotionmap.contracts.dictionary.Atmospheres;
import team4.emotionmap.contracts.geo.GeoPoint;

class JpaPreferenceHistoryReaderTest {

    private static final GeoPoint MAILBOX = new GeoPoint(37.5, 127.0);
    private static final Instant MAILBOX_ENABLED_AT = Instant.parse("2026-09-19T22:00:00Z");

    private final UserPreferenceVersionRepository repository = mock(UserPreferenceVersionRepository.class);
    private final JpaPreferenceHistoryReader reader = new JpaPreferenceHistoryReader(repository);

    private static UserPreferenceVersion version(UUID id, UUID userId, String description) {
        return UserPreferenceVersion.builder().id(id).userId(userId).revision(3L)
                .effectiveAt(Instant.parse("2026-09-19T23:40:00Z"))
                .mailboxLat(MAILBOX.lat()).mailboxLng(MAILBOX.lng()).mailboxEnabledAt(MAILBOX_ENABLED_AT)
                .crowdLevel(-0.5).spatialFeel(1).companyFit(0).stayStyle(-1)
                .description(description).build();
    }

    @Test
    void findAtMapsHistoricalMailboxEpochAndNormalizesBlankDescription() {
        UUID user = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        Instant cutoff = Instant.parse("2026-09-20T00:00:00Z");
        when(repository.findFirstByUserIdAndEffectiveAtLessThanEqualOrderByEffectiveAtDescRevisionDesc(user, cutoff))
                .thenReturn(Optional.of(version(id, user, "   ")));

        var snapshot = reader.findAt(user, cutoff).orElseThrow();

        assertThat(snapshot.versionId()).isEqualTo(id);
        assertThat(snapshot.revision()).isEqualTo(3L);
        assertThat(snapshot.atmospheres()).isEqualTo(new Atmospheres(-0.5, 1, 0, -1));
        assertThat(snapshot.description()).isNull();
        assertThat(snapshot.axisDefinitionVersion()).isEqualTo(2);
        assertThat(snapshot.mailbox()).isEqualTo(MAILBOX);
        assertThat(snapshot.mailboxEnabledAt()).isEqualTo(MAILBOX_ENABLED_AT);
    }

    @Test
    void findVersionHidesForeignVersion() {
        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(version(id, owner, "조용한 곳")));

        assertThat(reader.findVersion(owner, id)).map(s -> s.description()).contains("조용한 곳");
        assertThat(reader.findVersion(UUID.randomUUID(), id)).isEmpty();
    }
}
