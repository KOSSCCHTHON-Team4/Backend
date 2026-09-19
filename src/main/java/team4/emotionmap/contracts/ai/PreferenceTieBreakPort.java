package team4.emotionmap.contracts.ai;

/**
 * AI 담당 구현 + BE1 어댑터 · BE2 소비 (C09/B01). 최고점 동률 후보 + 자연어 취향 → 순위 또는 실패.
 * 최종 최고점 범위 검사·실패 시 무작위 처리는 BE2 책임. 상류 실패는 {@link TieBreakResult#failed}.
 */
public interface PreferenceTieBreakPort {

    TieBreakResult rank(TieBreakRequest request);
}
