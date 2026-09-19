package team4.emotionmap.account;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface UserPreferenceVersionRepository extends Repository<UserPreferenceVersion, UUID> {
    UserPreferenceVersion save(UserPreferenceVersion version);

    Optional<UserPreferenceVersion> findById(UUID id);

    Optional<UserPreferenceVersion> findByUserIdAndRevision(UUID userId, Long revision);

    Optional<UserPreferenceVersion> findFirstByUserIdOrderByRevisionDesc(UUID userId);

    Optional<UserPreferenceVersion> findFirstByUserIdAndEffectiveAtLessThanEqualOrderByEffectiveAtDescRevisionDesc(
            UUID userId, Instant cutoff);
}
