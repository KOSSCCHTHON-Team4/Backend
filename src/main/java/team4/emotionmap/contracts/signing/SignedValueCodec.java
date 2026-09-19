package team4.emotionmap.contracts.signing;

import java.time.Instant;
import java.util.UUID;

/**
 * BE1 제공 · BE1/BE2 소비. 분석 확인값·커서용 서명/검증(SHARED_CONTRACTS §3).
 *
 * <p>검증 실패 분류(구현이 {@code ContractError} 로 던진다):
 * <ul>
 *   <li>서명 불일치·형식 오류·용도 불일치·사용자 불일치·버전 불일치 → 용도별 "INVALID"
 *       (ANALYSIS_TOKEN → ANALYSIS_TOKEN_INVALID 422, PAGE_CURSOR → INVALID_CURSOR 400)</li>
 *   <li>만료 → ANALYSIS_TOKEN → ANALYSIS_TOKEN_EXPIRED 422, PAGE_CURSOR → INVALID_CURSOR 400</li>
 * </ul>
 * 발급된 값은 영구 저장하지 않는다(원문-사본 추적 키 금지). 클레임은 서명되지만 <b>암호화되지 않으므로</b>
 * 비밀·원문을 넣지 않는다.
 */
public interface SignedValueCodec {

    String sign(SignedClaims claims);

    /**
     * @param expectedPurpose 이 경로가 받는 용도. 다른 용도의 값은 무조건 INVALID.
     * @param expectedUserId  로그인 사용자. 다른 사용자의 값은 INVALID(존재 여부 비노출).
     * @param expectedVersion 이 경로가 해석할 수 있는 페이로드 버전.
     */
    SignedClaims verify(String token, SignedValuePurpose expectedPurpose, UUID expectedUserId,
                        int expectedVersion, Instant now);
}
