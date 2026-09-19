package team4.emotionmap.letter;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface LetterDeliveryRepository extends Repository<LetterDelivery, UUID> {

    Optional<LetterDelivery> findById(UUID id);

    LetterDelivery save(LetterDelivery delivery);

    List<LetterDelivery> findByReceiverIdOrderByDeliveredAtDescIdDesc(UUID receiverId);

    boolean existsByReceiverIdAndMemoryId(UUID receiverId, UUID memoryId);

    @Query("select d.memoryId from LetterDelivery d where d.id = :id and d.receiverId = :receiverId")
    Optional<UUID> findMemoryIdByIdAndReceiverId(@Param("id") UUID id, @Param("receiverId") UUID receiverId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from LetterDelivery d where d.id = :id and d.receiverId = :receiverId")
    Optional<LetterDelivery> findByIdAndReceiverIdForUpdate(@Param("id") UUID id, @Param("receiverId") UUID receiverId);
}
