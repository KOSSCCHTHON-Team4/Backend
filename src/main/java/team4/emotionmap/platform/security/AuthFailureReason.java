package team4.emotionmap.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import team4.emotionmap.contracts.error.ErrorCode;

/** Bearer 검증 실패 사유. 필터가 요청 속성에 남기고 EntryPoint 가 401 코드를 고른다. */
public enum AuthFailureReason {
    MISSING(ErrorCode.AUTH_REQUIRED),
    EXPIRED(ErrorCode.TOKEN_EXPIRED),
    INVALID(ErrorCode.INVALID_TOKEN);

    static final String ATTRIBUTE = AuthFailureReason.class.getName();

    private final ErrorCode errorCode;

    AuthFailureReason(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    static void record(HttpServletRequest request, AuthFailureReason reason) {
        request.setAttribute(ATTRIBUTE, reason);
    }

    static AuthFailureReason of(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        return value instanceof AuthFailureReason r ? r : MISSING;
    }
}
