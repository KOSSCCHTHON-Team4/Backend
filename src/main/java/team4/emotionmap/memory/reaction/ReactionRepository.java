package team4.emotionmap.memory.reaction;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    List<Reaction> findByMemoryId(Long memoryId);

    boolean existsByUserIdAndMemoryId(Long userId, Long memoryId);

    long countByMemoryId(Long memoryId);
}
