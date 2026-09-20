package team4.emotionmap.contracts.memory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Read-side bulk projection port for existing internal {@link MemorySnapshot} values.
 *
 * <p>This is intentionally not an HTTP DTO: snapshots retain owner and storage metadata for
 * internal policy/assembly only. Implementations must preserve category slot order and return an
 * entry only for a source row that exists at read time.
 */
public interface MemorySnapshotReader {

    Map<UUID, MemorySnapshot> readAll(Set<UUID> memoryIds);
}
