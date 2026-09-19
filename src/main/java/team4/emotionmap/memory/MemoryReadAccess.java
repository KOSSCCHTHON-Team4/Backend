package team4.emotionmap.memory;

import java.util.UUID;

/** A delivery grants recipient access; it never overrides current source safety. */
public interface MemoryReadAccess {
    boolean hasDelivery(UUID receiverId, UUID memoryId);
}
