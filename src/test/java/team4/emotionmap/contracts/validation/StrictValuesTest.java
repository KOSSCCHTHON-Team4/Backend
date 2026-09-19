package team4.emotionmap.contracts.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import org.junit.jupiter.api.Test;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.error.FieldError;
import team4.emotionmap.contracts.error.FieldErrorReason;

class StrictValuesTest {

    @Test
    void uuidMustBeCanonical() {
        assertThat(StrictValues.isUuid("10000000-0000-4000-8000-000000000001")).isTrue();
        assertThat(StrictValues.isUuid("1-2-3-4-5")).isFalse();          // UUID.fromString 은 통과시키는 표기
        assertThat(StrictValues.isUuid("100000000000400080000000000000001")).isFalse();
        assertThat(StrictValues.isUuid(null)).isFalse();

        ContractError e = catchThrowableOfType(ContractError.class,
                () -> StrictValues.requireUuid("nope", "imageId", ErrorCode.VALIDATION_ERROR));
        assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(e.fieldErrors()).containsExactly(new FieldError("imageId", FieldErrorReason.INVALID_VALUE));
        assertThat(StrictValues.optionalUuid(null, "placeId", ErrorCode.VALIDATION_ERROR)).isNull();
    }

    @Test
    void offsetDateTimeRequiresOffset() {
        assertThat(StrictValues.requireOffsetDateTime("2026-09-19T11:00:00+09:00", "t", ErrorCode.VALIDATION_ERROR)
                .getOffset().getId()).isEqualTo("+09:00");
        assertThat(StrictValues.requireOffsetDateTime("2026-09-19T02:00:00Z", "t", ErrorCode.VALIDATION_ERROR))
                .isNotNull();
        ContractError e = catchThrowableOfType(ContractError.class,
                () -> StrictValues.requireOffsetDateTime("2026-09-19T11:00:00", "t", ErrorCode.VALIDATION_ERROR));
        assertThat(e.fieldErrors()).containsExactly(FieldError.invalid("t"));
    }

    @Test
    void coordinatesReportBothAxes() {
        ContractError e = catchThrowableOfType(ContractError.class,
                () -> StrictValues.requireCoordinates(91.0, null, "lat", "lng", ErrorCode.VALIDATION_ERROR));
        assertThat(e.fieldErrors()).containsExactly(FieldError.outOfRange("lat"), FieldError.required("lng"));
        assertThat(StrictValues.requireCoordinates(37.6109, 126.9977, "lat", "lng", ErrorCode.VALIDATION_ERROR).lat())
                .isEqualTo(37.6109);
    }
}
