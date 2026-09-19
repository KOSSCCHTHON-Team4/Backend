package team4.emotionmap.contracts.ai;

/**
 * AI 담당 구현 + BE 어댑터 · notification 모듈 소비. 기획 §2 "설명: 알림 문구 생성"({@code POST /ai/match-reason}).
 * 실패해도 알림 자체는 막지 않는다 — 호출자가 템플릿 문구를 쓴다.
 */
public interface MatchReasonPort {

    MatchReasonResult explain(MatchReasonRequest request);
}
