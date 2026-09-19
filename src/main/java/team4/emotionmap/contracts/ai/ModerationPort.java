package team4.emotionmap.contracts.ai;

/**
 * AI 담당 구현 + BE1 어댑터 · BE1 소비 (C09, A07). 본문·선택 이미지 → 안전 판정.
 * 상류 실패는 {@link ModerationVerdict#ERROR} 결과로, 우리 쪽 장애만 {@link AiAdapterException}.
 * 판정은 배달 가능 여부에만 쓰이며 개인정보·익명·접근 허용을 결정하지 않는다(§4.2).
 */
public interface ModerationPort {

    ModerationResult moderate(ModerationRequest request);
}
