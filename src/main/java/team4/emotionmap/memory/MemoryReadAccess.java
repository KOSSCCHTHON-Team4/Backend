package team4.emotionmap.memory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** A delivery grants recipient access; it never overrides current source safety. */
public interface MemoryReadAccess {
    boolean hasDelivery(UUID receiverId, UUID memoryId);

    /**
     * Delivery metadata keyed by source memory id. It contains no author or independent-copy ID.
     */
    Map<UUID, DeliverySnapshot> findDeliveries(UUID receiverId, Set<UUID> memoryIds);

    /** Actual successful one-way copy counts, keyed for every requested source memory. */
    Map<UUID, Long> countLikes(Set<UUID> memoryIds);

    record DeliverySnapshot(UUID deliveryId, UUID memoryId, LocalDate serviceDate, Instant deliveredAt,
                            Instant readAt, Instant likedAt) {
        public DeliverySnapshot {
            Objects.requireNonNull(deliveryId, "deliveryId");
            Objects.requireNonNull(memoryId, "memoryId");
            Objects.requireNonNull(serviceDate, "serviceDate");
            Objects.requireNonNull(deliveredAt, "deliveredAt");
        }
    }
}
