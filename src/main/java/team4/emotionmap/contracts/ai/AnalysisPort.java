package team4.emotionmap.contracts.ai;

/**
 * AI 담당 구현 + BE1 어댑터 · BE1 소비 (C09). 본문 → 4축(null 가능)·카테고리 0~3.
 *
 * <p>계약:
 * <ul>
 *   <li>모델 시간 초과·5xx·형식 불량 응답은 {@link AnalysisResult#failed} 로 돌려준다(예외 아님). API 는 200+FAILED.</li>
 *   <li>어댑터 설정 누락 등 우리 쪽 장애만 {@link AiAdapterException} 으로 올린다(503 SERVICE_UNAVAILABLE).</li>
 *   <li>요청 본문·응답 원문을 INFO 로그에 남기지 않는다. 길이·상태·소요 시간만 남긴다.</li>
 *   <li>AI 는 DB 에 쓰지 않는다. 결과 저장·출처 판정(AI/USER)은 BE1 이 한다.</li>
 * </ul>
 */
public interface AnalysisPort {

    AnalysisResult analyze(AnalysisRequest request);
}
