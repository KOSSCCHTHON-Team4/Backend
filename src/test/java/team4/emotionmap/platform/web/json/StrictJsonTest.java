package team4.emotionmap.platform.web.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.Test;
import team4.emotionmap.contracts.dictionary.AnalyzedAtmospheres;
import team4.emotionmap.contracts.dictionary.AtmosphereAxis;
import team4.emotionmap.contracts.dictionary.Atmospheres;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.error.FieldError;
import team4.emotionmap.contracts.error.FieldErrorReason;
import team4.emotionmap.platform.web.GlobalExceptionHandlerSupport;
import tools.jackson.databind.json.JsonMapper;

/** A07: 축 JSON 은 원문 number·범위·키 규칙을 유지하며 0/분수를 canonical double 로 허용한다. */
class StrictJsonTest {

    record Wrapper(Atmospheres atmospheres, String note) {
    }

    record AnalyzedWrapper(AnalyzedAtmospheres atmospheres) {
    }

    private final JsonMapper mapper = StrictJson.mapper();

    private ContractError read(String json) {
        Throwable t = catchThrowable(() -> mapper.readValue(json, Wrapper.class));
        assertThat(t).as("expected rejection for %s", json).isNotNull();
        return GlobalExceptionHandlerSupport.translate(t);
    }

    private ContractError readAnalyzed(String json) {
        Throwable t = catchThrowable(() -> mapper.readValue(json, AnalyzedWrapper.class));
        assertThat(t).as("expected rejection for %s", json).isNotNull();
        return GlobalExceptionHandlerSupport.translate(t);
    }

    @Test
    void continuousAtmospheresRoundTripInCanonicalAxisOrder() {
        Wrapper w = mapper.readValue("""
                {"atmospheres":{"CROWD_LEVEL":-0,"SPATIAL_FEEL":0.25,"COMPANY_FIT":-1,"STAY_STYLE":10e-1},"note":null}
                """, Wrapper.class);
        assertThat(w.atmospheres()).isEqualTo(new Atmospheres(0.0, 0.25, -1.0, 1.0));
        assertThat(Double.doubleToLongBits(w.atmospheres().crowdLevel()))
                .isEqualTo(Double.doubleToLongBits(0.0));
        assertThat(mapper.writeValueAsString(w.atmospheres()))
                .isEqualTo("{\"CROWD_LEVEL\":0.0,\"SPATIAL_FEEL\":0.25,\"COMPANY_FIT\":-1.0,\"STAY_STYLE\":1.0}");

        AnalyzedWrapper analyzed = mapper.readValue("""
                {"atmospheres":{"CROWD_LEVEL":0,"SPATIAL_FEEL":null,"COMPANY_FIT":0.125,"STAY_STYLE":-1.0}}
                """, AnalyzedWrapper.class);
        assertThat(analyzed.atmospheres()).isEqualTo(new AnalyzedAtmospheres(0.0, null, 0.125, -1.0));
        assertThat(mapper.writeValueAsString(analyzed.atmospheres()))
                .isEqualTo("{\"CROWD_LEVEL\":0.0,\"SPATIAL_FEEL\":null,\"COMPANY_FIT\":0.125,\"STAY_STYLE\":-1.0}");
    }

    @Test
    void stringAndNullRemainInvalidWhileZeroAndFractionsAreAccepted() {
        ContractError e = read("""
                {"atmospheres":{"CROWD_LEVEL":"1","SPATIAL_FEEL":0,"COMPANY_FIT":null,"STAY_STYLE":0.5}}
                """);
        assertThat(e.code()).isEqualTo(ErrorCode.INVALID_ATMOSPHERES);
        assertThat(e.fieldErrors()).containsExactlyInAnyOrder(
                new FieldError("atmospheres.CROWD_LEVEL", FieldErrorReason.INVALID_VALUE),
                new FieldError("atmospheres.COMPANY_FIT", FieldErrorReason.REQUIRED));
    }

    @Test
    void preRoundingOutOfRangeLexemesAreInvalidAtmospheres() {
        ContractError positive = read("""
                {"atmospheres":{"CROWD_LEVEL":1.00000000000000000001,"SPATIAL_FEEL":0,"COMPANY_FIT":0,"STAY_STYLE":0}}
                """);
        assertThat(positive.code()).isEqualTo(ErrorCode.INVALID_ATMOSPHERES);
        assertThat(positive.fieldErrors()).containsExactly(FieldError.invalid("atmospheres.CROWD_LEVEL"));

        ContractError negative = read("""
                {"atmospheres":{"CROWD_LEVEL":-1.00000000000000000001,"SPATIAL_FEEL":0,"COMPANY_FIT":0,"STAY_STYLE":0}}
                """);
        assertThat(negative.code()).isEqualTo(ErrorCode.INVALID_ATMOSPHERES);
        assertThat(negative.fieldErrors()).containsExactly(FieldError.invalid("atmospheres.CROWD_LEVEL"));

        ContractError exponent = read("""
                {"atmospheres":{"CROWD_LEVEL":0.10000000000000000001e1,"SPATIAL_FEEL":0,"COMPANY_FIT":0,"STAY_STYLE":0}}
                """);
        assertThat(exponent.code()).isEqualTo(ErrorCode.INVALID_ATMOSPHERES);
        assertThat(exponent.fieldErrors()).containsExactly(FieldError.invalid("atmospheres.CROWD_LEVEL"));
    }

    @Test
    void analyzedAtmospheresRequireEveryKeyButPermitOnlyExplicitNulls() {
        ContractError e = readAnalyzed("""
                {"atmospheres":{"CROWD_LEVEL":0,"SPATIAL_FEEL":null,"COMPANY_FIT":false,"MOOD":0}}
                """);
        assertThat(e.code()).isEqualTo(ErrorCode.INVALID_ATMOSPHERES);
        assertThat(e.fieldErrors()).containsExactlyInAnyOrder(
                FieldError.invalid("atmospheres.COMPANY_FIT"),
                FieldError.unknown("atmospheres.MOOD"),
                FieldError.required("atmospheres.STAY_STYLE"));
    }

    @Test
    void missingAxisAndUnknownAxisAreReported() {
        ContractError e = read("""
                {"atmospheres":{"CROWD_LEVEL":1,"SPATIAL_FEEL":1,"COMPANY_FIT":1,"MOOD":1}}
                """);
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
        ContractError e = read("""
                {"atmospheres":{"CROWD_LEVEL":0,"CROWD_LEVEL":-1,"SPATIAL_FEEL":1,"COMPANY_FIT":1,"STAY_STYLE":1}}
                """);
        assertThat(e.code()).isEqualTo(ErrorCode.DUPLICATE_JSON_KEY);
        ContractError outer = read("{\"note\":\"a\",\"note\":\"b\"}");
        assertThat(outer.code()).isEqualTo(ErrorCode.DUPLICATE_JSON_KEY);
    }

    @Test
    void numberTokenLimitIsAnInvalidJsonStreamFailure() {
        String tooLongZero = "0." + "0".repeat(AtmosphereAxis.MAX_JSON_NUMBER_LENGTH - 1);
        ContractError e = read("""
                {"atmospheres":{"CROWD_LEVEL":%s,"SPATIAL_FEEL":0,"COMPANY_FIT":0,"STAY_STYLE":0}}
                """.formatted(tooLongZero));
        assertThat(e.code()).isEqualTo(ErrorCode.INVALID_JSON);
    }

    @Test
    void unknownTopLevelPropertyIsInvalidRequest() {
        ContractError e = read("""
                {"atmospheres":{"CROWD_LEVEL":0,"SPATIAL_FEEL":0,"COMPANY_FIT":0,"STAY_STYLE":0},"ownerId":"x"}
                """);
        assertThat(e.code()).isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(e.fieldErrors()).containsExactly(FieldError.unknown("ownerId"));
    }

    @Test
    void malformedJsonAndTrailingTokens() {
        assertThat(read("{\"atmospheres\":").code()).isEqualTo(ErrorCode.INVALID_JSON);
        String valid = "\"atmospheres\":{\"CROWD_LEVEL\":0,\"SPATIAL_FEEL\":0,\"COMPANY_FIT\":0,\"STAY_STYLE\":0}";
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
        record Login(String email, String password) {
        }
        record Num(Integer n, Boolean b) {
        }
        assertThat(GlobalExceptionHandlerSupport.translate(
                catchThrowable(() -> mapper.readValue("{\"n\":\"1\",\"b\":true}", Num.class))).code())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(GlobalExceptionHandlerSupport.translate(
                catchThrowable(() -> mapper.readValue("{\"n\":1,\"b\":\"true\"}", Num.class))).code())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(mapper.readValue("{\"email\":\"a@b\",\"password\":\"  p \"}", Login.class).password()).isEqualTo("  p ");
    }
}
