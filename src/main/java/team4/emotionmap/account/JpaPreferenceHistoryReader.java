package team4.emotionmap.account;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import team4.emotionmap.contracts.account.PreferenceHistoryReader;
import team4.emotionmap.contracts.account.PreferenceVersionSnapshot;
import team4.emotionmap.contracts.dictionary.Atmospheres;

/**
 * {@link PreferenceHistoryReader} 의 JPA 구현(BE1 제공 · BE2 소비). 커밋된 {@code user_preference_versions} 행만 읽고
 * 절대 쓰지 않는다. 일일 선정은 cutoff 시점 버전을 고정한 뒤 재시도에서는 {@link #findVersion} 으로 같은 버전을 다시 읽는다.
 */
@Component
@RequiredArgsConstructor
public class JpaPreferenceHistoryReader implements PreferenceHistoryReader {

    private final UserPreferenceVersionRepository versions;

    @Override
    @Transactional(readOnly = true)
    public Optional<PreferenceVersionSnapshot> findAt(UUID userId, Instant cutoff) {
        return versions.findFirstByUserIdAndEffectiveAtLessThanEqualOrderByEffectiveAtDescRevisionDesc(userId, cutoff)
                .map(JpaPreferenceHistoryReader::toSnapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PreferenceVersionSnapshot> findVersion(UUID userId, UUID versionId) {
        return versions.findById(versionId)
                .filter(version -> version.getUserId().equals(userId))
                .map(JpaPreferenceHistoryReader::toSnapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PreferenceVersionSnapshot> findLatest(UUID userId) {
        return versions.findFirstByUserIdOrderByRevisionDesc(userId).map(JpaPreferenceHistoryReader::toSnapshot);
    }

    static PreferenceVersionSnapshot toSnapshot(UserPreferenceVersion version) {
        String description = version.getDescription();
        return new PreferenceVersionSnapshot(
                version.getId(),
                version.getUserId(),
                version.getRevision(),
                version.getEffectiveAt(),
                new Atmospheres(version.getCrowdLevel(), version.getSpatialFeel(),
                        version.getCompanyFit(), version.getStayStyle()),
                description == null || description.isBlank() ? null : description,
                version.getAxisDefinitionVersion(),
                User.normalizedMailbox(version.getMailboxLat(), version.getMailboxLng()),
                version.getMailboxEnabledAt());
    }
}
