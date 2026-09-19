package team4.emotionmap.contracts.ai;

/**
 * AI 담당 구현 + BE 어댑터 · notification 모듈 소비. 기획 §7 2차 판정({@code POST /ai/verify}).
 * 1차 벡터 점수가 애매 구간(verifyThreshold ≤ s &lt; notifyThreshold)인 후보만 부른다. 취향 문장이 없는 사용자는 부르지 않는다.
 * 상류 실패는 {@link VerifyResult#failed} (fit=false) — 예외 아님.
 */
public interface PreferenceVerifyPort {

    VerifyResult verify(VerifyRequest request);
}
