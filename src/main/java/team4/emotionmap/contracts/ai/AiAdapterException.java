package team4.emotionmap.contracts.ai;

import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;

/**
 * 어댑터 자체(설정 누락·직렬화 버그 등) 장애. 상류 AI 실패와 구분하기 위해 별도 타입이며
 * API 에서는 503 {@code SERVICE_UNAVAILABLE} 로 나간다.
 */
public class AiAdapterException extends ContractError {

    public AiAdapterException(String internalMessage, Throwable cause) {
        super(ErrorCode.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE.defaultMessage(),
                java.util.List.of(), null, cause == null ? new IllegalStateException(internalMessage) : cause);
    }
}
