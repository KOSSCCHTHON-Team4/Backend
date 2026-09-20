package team4.emotionmap.contracts.fakes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import team4.emotionmap.contracts.account.PreferenceHistoryReader;
import team4.emotionmap.contracts.account.PreferenceVersionSnapshot;
import team4.emotionmap.contracts.dictionary.AtmosphereAxis;
import team4.emotionmap.contracts.dictionary.Atmospheres;
import team4.emotionmap.contracts.geo.GeoPoint;

/**
 * 불변 취향 이력의 가짜 구현. {@link #append} 만 있고 수정·삭제는 없다(불변 규칙 6).
 * findAt 의 의미(effective_at <= cutoff 중 최대 revision)를 실제 구현이 그대로 따라야 한다.
 */
public final class InMemoryPreferenceHistoryReader implements PreferenceHistoryReader {

    private final Map<UUID, List<PreferenceVersionSnapshot>> versions = new ConcurrentHashMap<>();

    public synchronized PreferenceVersionSnapshot append(UUID userId, Atmospheres atmospheres, String description,
                                                         Instant effectiveAt, GeoPoint mailbox,
                                                         Instant mailboxEnabledAt) {
        List<PreferenceVersionSnapshot> list = versions.computeIfAbsent(userId, k -> new ArrayList<>());
        long revision = list.isEmpty() ? 1 : list.get(list.size() - 1).revision() + 1;
        PreferenceVersionSnapshot snapshot = new PreferenceVersionSnapshot(UUID.randomUUID(), userId, revision,
                effectiveAt, atmospheres, description, AtmosphereAxis.DEFINITION_VERSION, mailbox, mailboxEnabledAt);
        list.add(snapshot);
        return snapshot;
    }

    @Override
    public Optional<PreferenceVersionSnapshot> findAt(UUID userId, Instant cutoff) {
        return versions.getOrDefault(userId, List.of()).stream()
                .filter(v -> !v.effectiveAt().isAfter(cutoff))
                .max(Comparator.comparingLong(PreferenceVersionSnapshot::revision));
    }

    @Override
    public Optional<PreferenceVersionSnapshot> findVersion(UUID userId, UUID versionId) {
        return versions.getOrDefault(userId, List.of()).stream()
                .filter(version -> version.versionId().equals(versionId))
                .findFirst();
    }

    @Override
    public Optional<PreferenceVersionSnapshot> findLatest(UUID userId) {
        return versions.getOrDefault(userId, List.of()).stream()
                .max(Comparator.comparingLong(PreferenceVersionSnapshot::revision));
    }

    public List<PreferenceVersionSnapshot> all(UUID userId) {
        return List.copyOf(versions.getOrDefault(userId, List.of()));
    }
}
