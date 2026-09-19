package team4.emotionmap.contracts.fakes;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import team4.emotionmap.contracts.memory.ContentStatus;
import team4.emotionmap.contracts.memory.DistributionType;
import team4.emotionmap.contracts.memory.IndependentCopyCommand;
import team4.emotionmap.contracts.memory.MemoryLifecyclePort;
import team4.emotionmap.contracts.memory.MemorySnapshot;
import team4.emotionmap.contracts.memory.MemoryStore;
import team4.emotionmap.contracts.memory.ModerationStatus;
import team4.emotionmap.contracts.memory.OriginKind;

/**
 * {@link MemoryStore}+{@link MemoryLifecyclePort} 가짜 구현(BE2 like/삭제 use case 테스트용).
 * 사본은 새 UUID·PRIVATE·LETTER_COPY·available_at null 로 저장되며 원문 ID 는 어디에도 남지 않는다.
 */
public final class InMemoryMemoryStore implements MemoryStore, MemoryLifecyclePort {

    private final Map<UUID, MemorySnapshot> rows = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryMemoryStore(Clock clock) {
        this.clock = clock;
    }

    public InMemoryMemoryStore put(MemorySnapshot snapshot) {
        rows.put(snapshot.id(), snapshot);
        return this;
    }

    @Override
    public Optional<MemorySnapshot> lockAndRead(UUID memoryId) {
        return read(memoryId);
    }

    @Override
    public Optional<MemorySnapshot> read(UUID memoryId) {
        return Optional.ofNullable(rows.get(memoryId));
    }

    @Override
    public UUID insertIndependentCopy(IndependentCopyCommand c) {
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        rows.put(id, new MemorySnapshot(id, c.newOwnerId(), c.placeId(), DistributionType.PRIVATE,
                OriginKind.LETTER_COPY, c.dataOrigin(), ContentStatus.ACTIVE, ModerationStatus.NOT_REQUIRED, null,
                c.content(), c.placeLabelSnapshot(), c.location(), c.atmospheres(), c.atmosphereSources(),
                c.axisDefinitionVersion(), c.atmosphereAnalysisStatus(), c.categoryAnalysisStatus(),
                c.analysisModel(), c.analysisPromptVersion(), c.categories(), c.image(), now, null));
        return id;
    }

    @Override
    public boolean markDeleted(UUID memoryId) {
        return transition(memoryId, ContentStatus.DELETED, clock.instant());
    }

    @Override
    public boolean hide(UUID memoryId) {
        return transition(memoryId, ContentStatus.HIDDEN, null);
    }

    @Override
    public boolean unhide(UUID memoryId) {
        MemorySnapshot s = rows.get(memoryId);
        if (s == null || s.contentStatus() != ContentStatus.HIDDEN) {
            return false;
        }
        return transition(memoryId, ContentStatus.ACTIVE, null);
    }

    private boolean transition(UUID memoryId, ContentStatus target, Instant deletedAt) {
        MemorySnapshot s = rows.get(memoryId);
        if (s == null || s.contentStatus() == target) {
            return false;
        }
        rows.put(memoryId, new MemorySnapshot(s.id(), s.ownerId(), s.placeId(), s.distributionType(), s.originKind(),
                s.dataOrigin(), target, s.moderationStatus(), s.availableAt(), s.content(), s.placeLabelSnapshot(),
                s.location(), s.atmospheres(), s.atmosphereSources(), s.axisDefinitionVersion(),
                s.atmosphereAnalysisStatus(), s.categoryAnalysisStatus(), s.analysisModel(), s.analysisPromptVersion(),
                s.categories(), s.image(), s.createdAt(), deletedAt == null ? s.deletedAt() : deletedAt));
        return true;
    }

    public int size() {
        return rows.size();
    }
}
