package team4.emotionmap.contracts.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** API_SPEC 10장 표와 1:1 인지 고정한다. 코드 추가/상태 변경은 OpenAPI 와 같은 변경 단위여야 한다. */
class ErrorCodeTest {

    private static final Map<String, Integer> SPEC = Map.ofEntries(
            Map.entry("INVALID_CREDENTIALS", 401), Map.entry("EMAIL_ALREADY_EXISTS", 409),
            Map.entry("TOO_MANY_ATTEMPTS", 429), Map.entry("AUTH_REQUIRED", 401),
            Map.entry("TOKEN_EXPIRED", 401), Map.entry("INVALID_TOKEN", 401),
            Map.entry("INVITATION_REQUIRED", 403), Map.entry("ACCOUNT_SUSPENDED", 403),
            Map.entry("ACCOUNT_CLOSED", 403), Map.entry("ONBOARDING_REQUIRED", 403),
            Map.entry("INVALID_JSON", 400), Map.entry("DUPLICATE_JSON_KEY", 400),
            Map.entry("INVALID_REQUEST", 400), Map.entry("VALIDATION_ERROR", 422),
            Map.entry("INVALID_ATMOSPHERES", 422), Map.entry("INVALID_CATEGORIES", 422),
            Map.entry("IMMUTABLE_FIELD", 422), Map.entry("ONBOARDING_ALREADY_COMPLETED", 409),
            Map.entry("PREFERENCE_VERSION_CONFLICT", 409), Map.entry("RESOURCE_NOT_FOUND", 404),
            Map.entry("MEMORY_UNAVAILABLE", 410), Map.entry("IMAGE_NOT_FOUND", 404),
            Map.entry("IMAGE_UPLOAD_EXPIRED", 410), Map.entry("IMAGE_ALREADY_ATTACHED", 409),
            Map.entry("IMAGE_TOO_LARGE", 413), Map.entry("UNSUPPORTED_IMAGE_TYPE", 415),
            Map.entry("INVALID_IMAGE", 422), Map.entry("IMAGE_DIMENSIONS_EXCEEDED", 422),
            Map.entry("IMAGE_STORAGE_UNAVAILABLE", 503), Map.entry("IMAGE_FILE_UNAVAILABLE", 503),
            Map.entry("ANALYSIS_TOKEN_INVALID", 422), Map.entry("ANALYSIS_TOKEN_EXPIRED", 422),
            Map.entry("ANALYSIS_CONTENT_MISMATCH", 422), Map.entry("DAILY_WRITE_LIMIT_EXCEEDED", 429),
            Map.entry("RATE_LIMITED", 429), Map.entry("INVALID_CURSOR", 400),
            Map.entry("CURSOR_CONTEXT_MISMATCH", 400), Map.entry("INVALID_BBOX", 400),
            Map.entry("PLACE_COORDINATE_MISMATCH", 422), Map.entry("IDEMPOTENCY_KEY_REQUIRED", 400),
            Map.entry("IDEMPOTENCY_KEY_REUSED", 409), Map.entry("REQUEST_IN_PROGRESS", 409),
            Map.entry("COPY_FAILED", 503), Map.entry("CONFIGURATION_UNAVAILABLE", 503),
            Map.entry("SERVICE_UNAVAILABLE", 503), Map.entry("SERVER_ERROR", 500));

    @Test
    void everySpecCodeExistsWithSpecStatus() {
        assertThat(ErrorCode.values()).hasSize(SPEC.size());
        SPEC.forEach((name, status) ->
                assertThat(ErrorCode.valueOf(name).httpStatus()).as(name).isEqualTo(status));
    }

    @Test
    void messagesNeverEmpty() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.defaultMessage()).as(code.name()).isNotBlank();
        }
    }

    @Test
    void apiErrorAlwaysCarriesFiveKeys() {
        ApiError error = ApiError.of(ContractError.retryAfter(ErrorCode.TOO_MANY_ATTEMPTS, 42), "rid");
        assertThat(error.code()).isEqualTo(ErrorCode.TOO_MANY_ATTEMPTS);
        assertThat(error.message()).isEqualTo(ErrorCode.TOO_MANY_ATTEMPTS.defaultMessage());
        assertThat(error.retryAfterSeconds()).isEqualTo(42);
        assertThat(error.fieldErrors()).isEmpty();
        assertThat(error.requestId()).isEqualTo("rid");
    }
}
