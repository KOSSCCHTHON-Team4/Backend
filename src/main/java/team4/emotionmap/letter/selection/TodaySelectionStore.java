package team4.emotionmap.letter.selection;

import java.time.Instant;
import java.util.UUID;
import team4.emotionmap.letter.DailySelectionStatus;
import team4.emotionmap.memory.MemoryReadAccess;

/**
 * Read-only, statement-consistent view of today's delivery state.
 *
 * <p>Implementations must not claim, recover, create, or otherwise mutate a selection while
 * serving this read. {@code status} and {@code delivery} are independently nullable: no daily
 * slot and no delivery are distinct persisted facts.</p>
 */
public interface TodaySelectionStore {

    Snapshot read(UUID userId);

    record Snapshot(
            Instant serverTime,
            boolean eligibleAtCutoff,
            boolean configurationAvailableAtCutoff,
            DailySelectionStatus status,
            MemoryReadAccess.DeliverySnapshot delivery
    ) {}
}
