package team4.emotionmap.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import team4.emotionmap.domain.Reaction;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    List<Reaction> findByMemoryId(Long memoryId);

    boolean existsByUserIdAndMemoryId(Long userId, Long memoryId);

    long countByMemoryId(Long memoryId);
}
