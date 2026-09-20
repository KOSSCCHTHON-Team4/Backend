package team4.emotionmap.memory;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import team4.emotionmap.contracts.memory.ContentStatus;
import team4.emotionmap.contracts.memory.DistributionType;
import team4.emotionmap.contracts.memory.MemorySnapshot;
import team4.emotionmap.contracts.memory.ModerationStatus;

@Service
@RequiredArgsConstructor
public class MemoryAccessService {
    private final MemoryRepository memoryRepository;
    private final MemoryReadAccess memoryReadAccess;

    @Transactional(readOnly = true)
    public Memory requireReadable(UUID userId, UUID memoryId) {
        Memory memory = memoryRepository.findById(memoryId)
                .orElseThrow(MemoryAccessService::notFound);
        return requireReadable(userId, memory);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Memory requireReadableForUpdate(UUID userId, UUID memoryId) {
        Memory memory = memoryRepository.findByIdForUpdate(memoryId)
                .orElseThrow(MemoryAccessService::notFound);
        return requireReadable(userId, memory);
    }

    /**
     * Snapshot equivalent of the entity policy. The caller supplies bulk-loaded delivery metadata
     * so list projections never turn this access check into one delivery lookup per card.
     */
    public ReadableRole requireReadable(UUID userId, MemorySnapshot memory,
                                        MemoryReadAccess.DeliverySnapshot delivery) {
        ReadableRole role = readableRole(userId, memory, delivery);
        boolean owner = userId != null && userId.equals(memory.ownerId());
        boolean recipient = isRecipient(userId, memory, delivery);
        if (!owner && !recipient) {
            throw notFound();
        }
        if (role == null) {
            throw unavailable();
        }
        return role;
    }

    /**
     * Current list visibility after the query's SQL predicate has run. A null result means a
     * concurrent safety/access change made the projection no longer displayable.
     */
    public ReadableRole readableRole(UUID userId, MemorySnapshot memory,
                                     MemoryReadAccess.DeliverySnapshot delivery) {
        if (userId == null || memory == null || memory.contentStatus() != ContentStatus.ACTIVE) {
            return null;
        }
        if (userId.equals(memory.ownerId())) {
            return ReadableRole.OWNER;
        }
        if (isRecipient(userId, memory, delivery)
                && memory.moderationStatus() == ModerationStatus.APPROVED) {
            return ReadableRole.RECIPIENT;
        }
        return null;
    }

    private Memory requireReadable(UUID userId, Memory memory) {
        boolean owner = userId != null && userId.equals(memory.getOwnerId());
        boolean recipient = !owner && userId != null && memory.getDistributionType() == DistributionType.LETTER
                && memoryReadAccess.hasDelivery(userId, memory.getId());
        if (!owner && !recipient) {
            throw notFound();
        }
        if (memory.getContentStatus() != ContentStatus.ACTIVE
                || (!owner && memory.getModerationStatus() != ModerationStatus.APPROVED)) {
            throw unavailable();
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

    private static boolean isRecipient(UUID userId, MemorySnapshot memory,
                                       MemoryReadAccess.DeliverySnapshot delivery) {
        return userId != null
                && delivery != null
                && memory.distributionType() == DistributionType.LETTER
                && memory.id().equals(delivery.memoryId());
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.GONE, "MEMORY_UNAVAILABLE");
    }

    public enum ReadableRole {
        OWNER,
        RECIPIENT
    }
}
