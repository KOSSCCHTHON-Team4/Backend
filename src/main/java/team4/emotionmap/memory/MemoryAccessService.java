package team4.emotionmap.memory;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import team4.emotionmap.contracts.memory.ContentStatus;
import team4.emotionmap.contracts.memory.DistributionType;
import team4.emotionmap.contracts.memory.ModerationStatus;

@Service
@RequiredArgsConstructor
public class MemoryAccessService {
    private final MemoryRepository memoryRepository;
    private final MemoryReadAccess memoryReadAccess;

    @Transactional(readOnly = true)
    public Memory requireReadable(UUID userId, UUID memoryId) {
        Memory memory = memoryRepository.findById(memoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memory not found"));
        boolean owner = userId != null && userId.equals(memory.getOwnerId());
        boolean recipient = !owner && userId != null && memory.getDistributionType() == DistributionType.LETTER
                && memoryReadAccess.hasDelivery(userId, memoryId);
        if (!owner && !recipient) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Memory not found");
        }
        if (memory.getContentStatus() != ContentStatus.ACTIVE
                || (!owner && memory.getModerationStatus() != ModerationStatus.APPROVED)) {
            throw new ResponseStatusException(HttpStatus.GONE, "Memory unavailable");
        }
        return memory;
    }

    public boolean isReadable(UUID userId, Memory memory) {
        if (userId == null || memory.getContentStatus() != ContentStatus.ACTIVE) {
            return false;
        }
        return userId.equals(memory.getOwnerId())
                || (memory.getDistributionType() == DistributionType.LETTER
                    && memory.getModerationStatus() == ModerationStatus.APPROVED
                    && memoryReadAccess.hasDelivery(userId, memory.getId()));
    }
}
