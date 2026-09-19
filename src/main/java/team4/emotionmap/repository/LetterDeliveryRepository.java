package team4.emotionmap.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import team4.emotionmap.domain.LetterDelivery;

public interface LetterDeliveryRepository extends JpaRepository<LetterDelivery, Long> {

    List<LetterDelivery> findByReceiverId(Long receiverId);
}
