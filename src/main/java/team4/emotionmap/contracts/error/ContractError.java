package team4.emotionmap.contracts.error;

import java.util.List;
import java.util.Objects;

/**
 * 계약 위반을 나타내는 유일한 비즈니스 예외. 어느 모듈에서 던져도 platform 의 전역 핸들러가
 * {@link ApiError} 로 바꾼다(SHARED_CONTRACTS §3 "ContractError").
 *
 * <p>message 는 사용자 표시용이다. SQL·파일 경로·다른 사용자 정보·본문 원문을 넣지 않는다.
 * 기본값은 {@link ErrorCode#defaultMessage()} 다.
 */
public class ContractError extends RuntimeException {

    private final ErrorCode code;
    private final List<FieldError> fieldErrors;
    private final Integer retryAfterSeconds;

    public ContractError(ErrorCode code) {
        this(code, code.defaultMessage(), List.of(), null, null);
    }

    public ContractError(ErrorCode code, List<FieldError> fieldErrors) {
        this(code, code.defaultMessage(), fieldErrors, null, null);
    }

    public ContractError(ErrorCode code, String message, List<FieldError> fieldErrors,
                         Integer retryAfterSeconds, Throwable cause) {
        super(message == null ? code.defaultMessage() : message, cause);
        this.code = Objects.requireNonNull(code, "code");
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
        if (retryAfterSeconds != null && retryAfterSeconds < 0) {
            throw new IllegalArgumentException("retryAfterSeconds must be >= 0");
        }
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static ContractError of(ErrorCode code) {
        return new ContractError(code);
    }

    public static ContractError of(ErrorCode code, FieldError... fieldErrors) {
        return new ContractError(code, List.of(fieldErrors));
    }

    public static ContractError of(ErrorCode code, List<FieldError> fieldErrors) {
        return new ContractError(code, fieldErrors);
    }

    /** 429·재시도 가능한 409/503 용. 서버가 계산한 실제 잔여 초를 넣는다. */
    public static ContractError retryAfter(ErrorCode code, int retryAfterSeconds) {
        return new ContractError(code, code.defaultMessage(), List.of(), retryAfterSeconds, null);
    }

    public static ContractError withCause(ErrorCode code, Throwable cause) {
        return new ContractError(code, code.defaultMessage(), List.of(), null, cause);
    }

    public ErrorCode code() {
        return code;
    }

    public List<FieldError> fieldErrors() {
        return fieldErrors;
    }

    public Integer retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public int httpStatus() {
        return code.httpStatus();
    }
}
