package team4.emotionmap.account;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team4.emotionmap.contracts.account.PreferenceHistoryReader;
import team4.emotionmap.contracts.account.PreferenceVersionSnapshot;
import team4.emotionmap.contracts.dictionary.Atmospheres;

/** Production adapter for immutable account preference and mailbox history. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserPreferenceHistoryReader implements PreferenceHistoryReader {

    private final UserPreferenceVersionRepository preferenceRepository;

    @Override
    public Optional<PreferenceVersionSnapshot> findAt(UUID userId, Instant cutoff) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(cutoff, "cutoff");
        return preferenceRepository
                .findFirstByUserIdAndEffectiveAtLessThanEqualOrderByRevisionDesc(userId, cutoff)
                .map(UserPreferenceHistoryReader::snapshot);
    }

    @Override
    public Optional<PreferenceVersionSnapshot> findVersion(UUID userId, UUID versionId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(versionId, "versionId");
        return preferenceRepository.findByIdAndUserId(versionId, userId)
                .map(UserPreferenceHistoryReader::snapshot);
    }

    @Override
    public Optional<PreferenceVersionSnapshot> findLatest(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        return preferenceRepository.findFirstByUserIdOrderByRevisionDesc(userId)
                .map(UserPreferenceHistoryReader::snapshot);
    }

    private static PreferenceVersionSnapshot snapshot(UserPreferenceVersion version) {
        return new PreferenceVersionSnapshot(
                version.getId(),
                version.getUserId(),
                version.getRevision(),
                version.getEffectiveAt(),
                new Atmospheres(
                        version.getCrowdLevel(),
                        version.getSpatialFeel(),
                        version.getCompanyFit(),
                        version.getStayStyle()),
                version.getDescription(),
                version.getAxisDefinitionVersion(),
                User.normalizedMailbox(version.getMailboxLat(), version.getMailboxLng()),
                version.getMailboxEnabledAt());
    }
}
