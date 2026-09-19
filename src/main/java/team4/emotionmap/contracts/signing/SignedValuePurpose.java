package team4.emotionmap.contracts.signing;

/**
 * 서명값 용도. 용도별로 키를 파생하고 검증 시 용도를 강제하므로
 * 인증 토큰(JWT, 별도 체계)·분석 확인값·커서를 서로 재사용할 수 없다.
 */
public enum SignedValuePurpose {
    /** {@code POST /memories/analyze} 응답의 analysisToken. */
    ANALYSIS_TOKEN,
    /** 목록 API 의 nextCursor. 사용자·경로·필터에 귀속. */
    PAGE_CURSOR
}
