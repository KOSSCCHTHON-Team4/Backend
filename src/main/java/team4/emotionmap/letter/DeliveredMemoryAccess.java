package team4.emotionmap.letter;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import team4.emotionmap.memory.MemoryReadAccess;

/**
 * Letter-owned delivery metadata implementation. Querying it from memory never injects the
 * memory HTTP projection back into letter.
 */
@Component
@RequiredArgsConstructor
public class DeliveredMemoryAccess implements MemoryReadAccess {

    private final LetterReadJdbcQuery letterReadJdbcQuery;

    @Override
    public boolean hasDelivery(UUID receiverId, UUID memoryId) {
        return letterReadJdbcQuery.hasDelivery(receiverId, memoryId);
    }

    @Override
    public Map<UUID, DeliverySnapshot> findDeliveries(UUID receiverId, Set<UUID> memoryIds) {
        return letterReadJdbcQuery.findDeliveries(receiverId, memoryIds);
    }

    @Override
    public Map<UUID, Long> countLikes(Set<UUID> memoryIds) {
        return letterReadJdbcQuery.countLikes(memoryIds);
    }
}
