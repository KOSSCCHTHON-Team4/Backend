package team4.emotionmap.platform.web.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.List;
import org.junit.jupiter.api.Test;
import team4.emotionmap.contracts.dictionary.AnalyzedAtmospheres;
import team4.emotionmap.contracts.dictionary.Atmospheres;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.error.FieldError;
import team4.emotionmap.contracts.error.FieldErrorReason;
import team4.emotionmap.platform.web.GlobalExceptionHandlerSupport;
import tools.jackson.databind.json.JsonMapper;

/** A07: 축 누락·0·소수·문자열·null·중복 키를 파서 수준에서 거절한다. */
class StrictJsonTest {

    record Wrapper(Atmospheres atmospheres, String note) {
    }

    private final JsonMapper mapper = StrictJson.mapper();

    private ContractError read(String json) {
        Throwable t = catchThrowable(() -> mapper.readValue(json, Wrapper.class));
        assertThat(t).as("expected rejection for %s", json).isNotNull();
        return GlobalExceptionHandlerSupport.translate(t);
    }

    @Test
    void validAtmospheresRoundTrip() {
        Wrapper w = mapper.readValue("{\"atmospheres\":{\"CROWD_LEVEL\":-1,\"SPATIAL_FEEL\":1,\"COMPANY_FIT\":-1,\"STAY_STYLE\":1},\"note\":null}", Wrapper.class);
        assertThat(w.atmospheres()).isEqualTo(new Atmospheres(-1, 1, -1, 1));
        assertThat(mapper.writeValueAsString(w.atmospheres()))
                .isEqualTo("{\"CROWD_LEVEL\":-1,\"SPATIAL_FEEL\":1,\"COMPANY_FIT\":-1,\"STAY_STYLE\":1}");
        assertThat(mapper.writeValueAsString(new AnalyzedAtmospheres(-1, null, 1, null)))
                .isEqualTo("{\"CROWD_LEVEL\":-1,\"SPATIAL_FEEL\":null,\"COMPANY_FIT\":1,\"STAY_STYLE\":null}");
    }

    @Test
    void decimalLiteralIsRejectedEvenWhenMathematicallyInteger() {
        ContractError e = read("{\"atmospheres\":{\"CROWD_LEVEL\":1.0,\"SPATIAL_FEEL\":1,\"COMPANY_FIT\":1,\"STAY_STYLE\":1}}");
        assertThat(e.code()).isEqualTo(ErrorCode.INVALID_ATMOSPHERES);
        assertThat(e.fieldErrors()).containsExactly(new FieldError("atmospheres.CROWD_LEVEL", FieldErrorReason.INVALID_VALUE));
    }

    @Test
    void stringZeroNullAndHalfAreRejected() {
        ContractError e = read("{\"atmospheres\":{\"CROWD_LEVEL\":\"1\",\"SPATIAL_FEEL\":0,\"COMPANY_FIT\":null,\"STAY_STYLE\":0.5}}");
        assertThat(e.code()).isEqualTo(ErrorCode.INVALID_ATMOSPHERES);
        assertThat(e.fieldErrors()).containsExactlyInAnyOrder(
                new FieldError("atmospheres.CROWD_LEVEL", FieldErrorReason.INVALID_VALUE),
                new FieldError("atmospheres.SPATIAL_FEEL", FieldErrorReason.INVALID_VALUE),
                new FieldError("atmospheres.COMPANY_FIT", FieldErrorReason.REQUIRED),
                new FieldError("atmospheres.STAY_STYLE", FieldErrorReason.INVALID_VALUE));
    }

    @Test
    void missingAxisAndUnknownAxisAreReported() {
        ContractError e = read("{\"atmospheres\":{\"CROWD_LEVEL\":1,\"SPATIAL_FEEL\":1,\"COMPANY_FIT\":1,\"MOOD\":1}}");
        assertThat(e.code()).isEqualTo(ErrorCode.INVALID_ATMOSPHERES);
        assertThat(e.fieldErrors()).containsExactlyInAnyOrder(
                new FieldError("atmospheres.MOOD", FieldErrorReason.UNKNOWN_FIELD),
                new FieldError("atmospheres.STAY_STYLE", FieldErrorReason.REQUIRED));
    }

    @Test
    void nullAtmospheresObjectIsRequired() {
        ContractError e = read("{\"atmospheres\":null}");
        assertThat(e.code()).isEqualTo(ErrorCode.INVALID_ATMOSPHERES);
        assertThat(e.fieldErrors()).containsExactly(FieldError.required("atmospheres"));
    }

    @Test
    void duplicateJsonKeyIsRejectedByParser() {
        ContractError e = read("{\"atmospheres\":{\"CROWD_LEVEL\":1,\"CROWD_LEVEL\":-1,\"SPATIAL_FEEL\":1,\"COMPANY_FIT\":1,\"STAY_STYLE\":1}}");
        assertThat(e.code()).isEqualTo(ErrorCode.DUPLICATE_JSON_KEY);
        ContractError outer = read("{\"note\":\"a\",\"note\":\"b\"}");
        assertThat(outer.code()).isEqualTo(ErrorCode.DUPLICATE_JSON_KEY);
    }

    @Test
    void unknownTopLevelPropertyIsInvalidRequest() {
        ContractError e = read("{\"atmospheres\":{\"CROWD_LEVEL\":1,\"SPATIAL_FEEL\":1,\"COMPANY_FIT\":1,\"STAY_STYLE\":1},\"ownerId\":\"x\"}");
        assertThat(e.code()).isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(e.fieldErrors()).containsExactly(FieldError.unknown("ownerId"));
    }

    @Test
    void malformedJsonAndTrailingTokens() {
        assertThat(read("{\"atmospheres\":").code()).isEqualTo(ErrorCode.INVALID_JSON);
        String valid = "\"atmospheres\":{\"CROWD_LEVEL\":1,\"SPATIAL_FEEL\":1,\"COMPANY_FIT\":1,\"STAY_STYLE\":1}";
        assertThat(read("{" + valid + ",\"note\":\"a\"} extra").code()).isEqualTo(ErrorCode.INVALID_JSON);
    }

    @Test
    void missingAtmospheresPropertyIsRequired() {
        ContractError e = read("{\"note\":\"a\"}");
        assertThat(e.code()).isEqualTo(ErrorCode.INVALID_ATMOSPHERES);
        assertThat(e.fieldErrors()).containsExactly(FieldError.required("atmospheres"));
    }

    @Test
    void scalarCoercionIsDisabled() {
        record Login(String email, String password) { }
        record Num(Integer n, Boolean b) { }
        assertThat(GlobalExceptionHandlerSupport.translate(
                catchThrowable(() -> mapper.readValue("{\"n\":\"1\",\"b\":true}", Num.class))).code())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(GlobalExceptionHandlerSupport.translate(
                catchThrowable(() -> mapper.readValue("{\"n\":1,\"b\":\"true\"}", Num.class))).code())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(mapper.readValue("{\"email\":\"a@b\",\"password\":\"  p \"}", Login.class).password()).isEqualTo("  p ");
        assertThat(List.of(1)).hasSize(1);
    }
}
