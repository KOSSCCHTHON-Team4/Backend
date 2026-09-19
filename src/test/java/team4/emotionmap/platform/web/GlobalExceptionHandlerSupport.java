package team4.emotionmap.platform.web;

import team4.emotionmap.contracts.error.ContractError;

/** 테스트에서 역직렬화 예외 → ContractError 변환 규칙을 직접 검증하기 위한 진입점. */
public final class GlobalExceptionHandlerSupport {

    private GlobalExceptionHandlerSupport() {
    }

    public static ContractError translate(Throwable t) {
        return GlobalExceptionHandler.translateUnreadable(t);
    }
}
