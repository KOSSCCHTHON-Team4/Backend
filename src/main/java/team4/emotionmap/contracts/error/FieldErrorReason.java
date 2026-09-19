package team4.emotionmap.contracts.error;

/** API_SPEC 9장 {@code FieldError.reason} 고정 enum. */
public enum FieldErrorReason {
    REQUIRED,
    INVALID_VALUE,
    DUPLICATE,
    OUT_OF_RANGE,
    UNKNOWN_FIELD,
    TOO_LONG,
    IMMUTABLE
}
