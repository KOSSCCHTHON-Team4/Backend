package team4.emotionmap.contracts.account;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * BE1 제공 · BE2 소비. 09:00 cutoff 시점에 유효했던 취향 버전을 돌려준다(SHARED_CONTRACTS §4.1).
 *
 * <p>규칙:
 * <ul>
 *   <li>{@code findAt(user, cutoff)} = {@code effective_at <= cutoff} 인 버전 중 revision 최대값.</li>
 *   <li>복구 시점(예: 11:00)의 최신값으로 대체하지 않는다. 08:40 v1 → 10:00 v2 → 09:00 cutoff 는 v1.</li>
 *   <li>버전 값은 절대 삭제·덮어쓰기하지 않으므로 과거 cutoff 는 언제든 재현 가능해야 한다.</li>
 *   <li>정확히 cutoff 경계에 걸친 미커밋 변경은 C08/A02/B02 의 쓰기·스케줄 계약(D10)으로 별도 정한다.
 *       이 포트는 커밋된 행만 본다.</li>
 * </ul>
 */
public interface PreferenceHistoryReader {

    Optional<PreferenceVersionSnapshot> findAt(UUID userId, Instant cutoff);

    /**
     * Returns the immutable pinned version only when it belongs to {@code userId}.
     *
     * <p>Missing or foreign version IDs remain missing; callers must not substitute the current version.
     */
    Optional<PreferenceVersionSnapshot> findVersion(UUID userId, UUID versionId);

    /** 현재 최신 버전(프로필 조회용). 온보딩 전이면 empty. */
    Optional<PreferenceVersionSnapshot> findLatest(UUID userId);
}
