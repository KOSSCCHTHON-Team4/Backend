/**
 * 공동 계약(contracts) 모듈.
 *
 * <p>BE1·BE2·AI 담당이 함께 쓰는 <b>최소 인터페이스와 값 객체</b>만 둔다
 * (WORK_PLAN §8·§13.1, SHARED_CONTRACTS §3). 규칙:
 * <ul>
 *   <li>이 패키지는 {@code team4.emotionmap} 내부의 다른 모듈에 의존하지 않는다(외부 라이브러리만).</li>
 *   <li>다른 모든 모듈은 이 패키지에 의존할 수 있다. 서로의 Controller/Service/Entity 는 주입하지 않는다.</li>
 *   <li>포트 구현은 각 소유 모듈에 둔다. 여기에는 시그니처·불변 조건·값 객체만 둔다.</li>
 *   <li>파일별 주 담당: {@code error/page/dictionary/validation/geo/config/signing/account/memory/media/ai} = BE1.
 *       BE2 제공 공통 계약은 순수 {@code ServiceTime}(KST 서비스 날짜·09:00 cutoff)과
 *       {@code DistanceMeters}(IUGG 평균 지구 반지름 haversine 거리)다.</li>
 * </ul>
 */
package team4.emotionmap.contracts;
