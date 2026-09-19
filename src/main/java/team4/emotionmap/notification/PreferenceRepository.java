package team4.emotionmap.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreferenceRepository extends JpaRepository<Preference, UUID> {
    List<Preference> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<Preference> findByIdAndUserId(UUID id, UUID userId);
    List<Preference> findByActiveTrueAndUserIdNot(UUID excludedUserId);
}
