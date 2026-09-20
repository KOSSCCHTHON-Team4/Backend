package team4.emotionmap.account;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface UserPreferenceVersionRepository extends Repository<UserPreferenceVersion, UUID> {
    UserPreferenceVersion save(UserPreferenceVersion version);

    Optional<UserPreferenceVersion> findByIdAndUserId(UUID id, UUID userId);

    Optional<UserPreferenceVersion> findByUserIdAndRevision(UUID userId, Long revision);

    Optional<UserPreferenceVersion> findFirstByUserIdOrderByRevisionDesc(UUID userId);

    Optional<UserPreferenceVersion> findFirstByUserIdAndEffectiveAtLessThanEqualOrderByRevisionDesc(
            UUID userId, Instant cutoff);
}
