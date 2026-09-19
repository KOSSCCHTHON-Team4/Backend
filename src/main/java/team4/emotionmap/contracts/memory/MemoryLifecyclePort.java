package team4.emotionmap.contracts.memory;

import java.util.UUID;

/**
 * BE1 제공 · BE2 소비. 경험 상태 전이의 내부 쓰기. 호출자 트랜잭션에 참여하며 권한 판정은 하지 않는다
 * (소유자 확인·404/410 분류는 BE2 의 MemoryAccessPolicy/use case 책임).
 */
public interface MemoryLifecyclePort {

    /**
     * 본인 일반 삭제: {@code content_status=DELETED, deleted_at=now}. 이미 DELETED 면 false(멱등).
     * 원문 삭제는 기존 배달 이력·독립 사본을 건드리지 않는다.
     */
    boolean markDeleted(UUID memoryId);

    /** 운영 숨김: {@code content_status=HIDDEN}. 일반 삭제와 구분한다. 이미 HIDDEN 이면 false. */
    boolean hide(UUID memoryId);

    /** 운영 숨김 해제(ACTIVE 복귀). 최초 {@code available_at} 은 갱신하지 않는다. */
    boolean unhide(UUID memoryId);
}
