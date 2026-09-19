package team4.emotionmap.contracts.memory;

import java.util.Optional;
import java.util.UUID;

/**
 * BE1 제공 · BE2 소비. 공용 경험 저장 primitive(SHARED_CONTRACTS §3, §4.4).
 *
 * <p>모든 메서드는 <b>호출자의 DB 트랜잭션에 참여</b>한다({@code REQUIRED} 전파). 내부에서 별도 커밋·
 * 새 트랜잭션·HTTP 호출을 하지 않는다. 좋아요 전체의 성공/실패와 커밋은 BE2 use case 가 결정한다.
 */
public interface MemoryStore {

    /**
     * 원문 행을 {@code SELECT ... FOR UPDATE} 로 잠그고 스냅샷을 읽는다. 없는 행은 empty.
     * 잠금 순서(원문 → 수신 행)는 D10 공통 잠금 순서 계약을 따른다.
     */
    Optional<MemorySnapshot> lockAndRead(UUID memoryId);

    /** 잠금 없는 스냅샷 읽기(권한 판정 전 상태 확인용). */
    Optional<MemorySnapshot> read(UUID memoryId);

    /**
     * 수신자 소유 PRIVATE/LETTER_COPY 와 memory_categories 를 INSERT 하고 새 ID 를 반환한다.
     * {@code moderation_status} 는 운영 계약(D09)에 따라 구현이 정하고, {@code available_at} 은 항상 NULL.
     * 어떤 컬럼에도 원문 ID 를 넣지 않는다.
     */
    UUID insertIndependentCopy(IndependentCopyCommand command);
}
