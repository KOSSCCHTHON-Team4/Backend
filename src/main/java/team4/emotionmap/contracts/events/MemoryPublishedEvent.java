package team4.emotionmap.contracts.events;

import java.util.Objects;
import java.util.UUID;

/**
 * 경험이 <b>타인에게 보일 수 있는 상태</b>가 되어 장소 벡터가 갱신되었음을 알리는 도메인 이벤트(기획 §1 "자동 알림").
 * LETTER 는 안전 승인(APPROVED) 시점에, PRIVATE 는 발행하지 않는다.
 * memory 모듈이 커밋 후 발행하고 notification 모듈이 구독한다. 두 모듈은 이 타입으로만 연결된다.
 */
public record MemoryPublishedEvent(UUID memoryId, UUID placeId, UUID ownerId) {

    public MemoryPublishedEvent {
        Objects.requireNonNull(memoryId);
        Objects.requireNonNull(placeId);
        Objects.requireNonNull(ownerId);
    }
}
