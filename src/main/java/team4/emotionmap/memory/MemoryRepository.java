package team4.emotionmap.memory;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemoryRepository extends JpaRepository<Memory, UUID> {
    List<Memory> findByOwnerIdAndContentStatusOrderByCreatedAtDesc(UUID ownerId, ContentStatus contentStatus);
    List<Memory> findByPlaceIdAndContentStatusOrderByCreatedAtDesc(UUID placeId, ContentStatus contentStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Memory m where m.id = :id")
    Optional<Memory> findByIdForUpdate(@Param("id") UUID id);
}
