package team4.emotionmap.platform.web.json;

import team4.emotionmap.contracts.dictionary.AtmosphereAxis;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * 엄격 JSON 규칙(C03). 앱의 {@code JsonMapper} 와 테스트/AI 어댑터의 독립 mapper 가 <b>같은 규칙</b>을 쓴다.
 * <ul>
 *   <li>중복 키 → 파서 예외(400 DUPLICATE_JSON_KEY)</li>
 *   <li>모르는 속성 → 예외(400 INVALID_REQUEST, UNKNOWN_FIELD) = {@code additionalProperties:false}</li>
 *   <li>축 JSON number 는 원문 lexical 범위 검사 뒤 double 로 변환하고, 다른 스칼라는 타입 강제 변환하지 않는다.</li>
 *   <li>primitive 에 null 거절, 본문 뒤 잉여 토큰 거절</li>
 * </ul>
 * 응답의 null 키는 항상 포함한다(기본 Include.ALWAYS 유지).
 */
public final class StrictJson {

    private static final JsonFactory MAPPER_FACTORY = JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNumberLength(AtmosphereAxis.MAX_JSON_NUMBER_LENGTH)
                    .build())
            .build();

    private StrictJson() {
    }

    public static JsonMapper.Builder apply(JsonMapper.Builder builder) {
        return builder
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .withCoercionConfig(LogicalType.Textual, cfg -> cfg
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                .addModule(new ContractsJacksonModule());
    }

    /** Spring 컨텍스트 없이 같은 규칙과 숫자 token 상한을 가진 mapper (단위 테스트·어댑터용). */
    public static JsonMapper mapper() {
        return apply(JsonMapper.builder(MAPPER_FACTORY)).build();
    }
}
