package team4.emotionmap.contracts.error;

import java.util.List;
import java.util.Objects;

/**
 * 모든 실패 응답의 본문(API_SPEC 2.3 / 9장 {@code ApiError}).
 * 다섯 키는 항상 포함한다. {@code retryAfterSeconds} 는 없으면 null 이고, 있으면
 * {@code Retry-After} 헤더와 같은 값이어야 한다.
 */
public record ApiError(
        ErrorCode code,
        String message,
        Integer retryAfterSeconds,
        List<FieldError> fieldErrors,
        String requestId
) {
    public ApiError {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(requestId, "requestId");
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public static ApiError of(ContractError error, String requestId) {
        return new ApiError(error.code(), error.getMessage(), error.retryAfterSeconds(),
                error.fieldErrors(), requestId);
    }

    public static ApiError of(ErrorCode code, String requestId) {
        return new ApiError(code, code.defaultMessage(), null, List.of(), requestId);
    }
}
