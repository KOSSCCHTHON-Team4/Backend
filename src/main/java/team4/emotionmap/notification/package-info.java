/**
 * notification 모듈 — 기획 "AI 어떻게 활용할까요?" §4·§7·§9.
 * 취향 키워드 카드 설정({@code preferences}), 새 리뷰 → 장소 벡터 갱신 → 저장된 취향 매칭(1차 코사인, 2차 AI 검증)
 * → 알림({@code notifications}). 온보딩 취향 이력·09:00 일일 배달(letter 모듈)과는 별개의 흐름이다.
 * memory 모듈과는 {@code contracts.events.MemoryPublishedEvent} 로만 연결된다.
 */
package team4.emotionmap.notification;
