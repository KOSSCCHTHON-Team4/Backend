package team4.emotionmap.notification;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId);
    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);
    boolean existsByPreferenceIdAndPlaceIdAndServiceDate(UUID preferenceId, UUID placeId, LocalDate serviceDate);
    long countByUserIdAndReadAtIsNull(UUID userId);
}
