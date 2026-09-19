package team4.emotionmap.contracts.error;

import java.util.Objects;

/**
 * 필드 단위 검증 실패. {@code field} 는 JSON 경로(예: {@code atmospheres.STAY_STYLE}),
 * 원문 값이나 비밀번호는 절대 넣지 않는다.
 */
public record FieldError(String field, FieldErrorReason reason) {

    public FieldError {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(reason, "reason");
    }

    public static FieldError required(String field) {
        return new FieldError(field, FieldErrorReason.REQUIRED);
    }

    public static FieldError invalid(String field) {
        return new FieldError(field, FieldErrorReason.INVALID_VALUE);
    }

    public static FieldError duplicate(String field) {
        return new FieldError(field, FieldErrorReason.DUPLICATE);
    }

    public static FieldError outOfRange(String field) {
        return new FieldError(field, FieldErrorReason.OUT_OF_RANGE);
    }

    public static FieldError unknown(String field) {
        return new FieldError(field, FieldErrorReason.UNKNOWN_FIELD);
    }

    public static FieldError tooLong(String field) {
        return new FieldError(field, FieldErrorReason.TOO_LONG);
    }

    public static FieldError immutable(String field) {
        return new FieldError(field, FieldErrorReason.IMMUTABLE);
    }
}
