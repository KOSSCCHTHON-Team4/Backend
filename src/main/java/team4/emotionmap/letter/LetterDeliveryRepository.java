package team4.emotionmap.letter;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LetterDeliveryRepository extends JpaRepository<LetterDelivery, Long> {

    List<LetterDelivery> findByReceiverId(Long receiverId);
}
