package team4.emotionmap.letter;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import team4.emotionmap.memory.MemoryReadAccess;

@Component
@RequiredArgsConstructor
public class DeliveredMemoryAccess implements MemoryReadAccess {

    private final LetterDeliveryRepository letterDeliveryRepository;

    @Override
    public boolean hasDelivery(UUID receiverId, UUID memoryId) {
        return letterDeliveryRepository.existsByReceiverIdAndMemoryId(receiverId, memoryId);
    }
}
