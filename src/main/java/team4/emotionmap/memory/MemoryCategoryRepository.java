package team4.emotionmap.memory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface MemoryCategoryRepository extends Repository<MemoryCategory, MemoryCategoryId> {
    Optional<MemoryCategory> findById(MemoryCategoryId id);
    List<MemoryCategory> findByIdMemoryIdOrderBySlotNo(UUID memoryId);
    MemoryCategory save(MemoryCategory category);
}
