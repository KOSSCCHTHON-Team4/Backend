package team4.emotionmap.platform.web.json;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import team4.emotionmap.contracts.dictionary.AnalyzedAtmospheres;
import team4.emotionmap.contracts.dictionary.AtmosphereAxis;
import team4.emotionmap.contracts.dictionary.Atmospheres;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.error.FieldError;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;

/**
 * 4축 JSON 의 <b>토큰 수준</b> 검사(API_SPEC 2.2, 불변 규칙 1).
 *
 * <p>JSON Schema 는 {@code 1} 과 {@code 1.0} 을 같은 정수로 볼 수 있으므로 여기서 파서 토큰을 직접 본다:
 * <ul>
 *   <li>{@code VALUE_NUMBER_INT} 이고 값이 -1/1 일 때만 허용</li>
 *   <li>{@code 1.0}·{@code 1e0}(FLOAT), {@code "1"}(STRING), {@code true}, 배열/객체 → INVALID_VALUE</li>
 *   <li>{@code null} → REQUIRED, 축 누락 → REQUIRED, 모르는 키 → UNKNOWN_FIELD, 같은 축 반복 → DUPLICATE</li>
 * </ul>
 * 모든 위반을 모아 {@code 422 INVALID_ATMOSPHERES} 로 던진다. 필드 경로는 {@code <속성명>.<AXIS>}.
 */
public final class AtmospheresJson {

    private static final String DEFAULT_PROPERTY = "atmospheres";

    private AtmospheresJson() {
    }

    /** {@link Atmospheres} 역직렬화기(요청용). */
    public static final class Deserializer extends ValueDeserializer<Atmospheres> {
        @Override
        public Atmospheres deserialize(JsonParser p, DeserializationContext ctxt) {
            String prefix = p.currentName() == null ? DEFAULT_PROPERTY : p.currentName();
            if (p.currentToken() == null) {
                // 잘린 JSON: 의미 검증(422)이 아니라 구문 오류(400 INVALID_JSON)다.
                throw new StreamReadException(p, "Unexpected end-of-input while reading atmospheres");
            }
            if (!p.isExpectedStartObjectToken()) {
                p.skipChildren();
                throw ContractError.of(ErrorCode.INVALID_ATMOSPHERES, FieldError.invalid(prefix));
            }
            Map<AtmosphereAxis, Integer> values = new EnumMap<>(AtmosphereAxis.class);
            EnumSet<AtmosphereAxis> touched = EnumSet.noneOf(AtmosphereAxis.class);
            List<FieldError> errors = new ArrayList<>();

            String name;
            while ((name = p.nextName()) != null) {
                JsonToken token = p.nextToken();
                String path = prefix + "." + name;
                AtmosphereAxis axis = axisOf(name);
                if (axis == null) {
                    errors.add(FieldError.unknown(path));
                    p.skipChildren();
                    continue;
                }
                if (!touched.add(axis)) {
                    errors.add(FieldError.duplicate(path));
                    p.skipChildren();
                    continue;
                }
                if (token == JsonToken.VALUE_NULL) {
                    errors.add(FieldError.required(path));
                    continue;
                }
                if (token != JsonToken.VALUE_NUMBER_INT || p.getNumberType() != JsonParser.NumberType.INT) {
                    errors.add(FieldError.invalid(path));
                    p.skipChildren();
                    continue;
                }
                int value = p.getIntValue();
                if (!AtmosphereAxis.isValidValue(value)) {
                    errors.add(FieldError.invalid(path));
                    continue;
                }
                values.put(axis, value);
            }
            for (AtmosphereAxis axis : AtmosphereAxis.values()) {
                if (!touched.contains(axis)) {
                    errors.add(FieldError.required(prefix + "." + axis.name()));
                }
            }
            if (!errors.isEmpty()) {
                throw ContractError.of(ErrorCode.INVALID_ATMOSPHERES, errors);
            }
            return Atmospheres.fromMap(values);
        }

        /** 속성 자체가 {@code null} 이면 축 4개 모두 REQUIRED. */
        @Override
        public Object getNullValue(DeserializationContext ctxt) {
            String prefix = ctxt.getParser() != null && ctxt.getParser().currentName() != null
                    ? ctxt.getParser().currentName() : DEFAULT_PROPERTY;
            throw ContractError.of(ErrorCode.INVALID_ATMOSPHERES, FieldError.required(prefix));
        }

        private static AtmosphereAxis axisOf(String name) {
            for (AtmosphereAxis axis : AtmosphereAxis.values()) {
                if (axis.name().equals(name)) {
                    return axis;
                }
            }
            return null;
        }
    }

    /** {@link Atmospheres} 직렬화기(응답용): 키는 축 코드, 값은 정수. */
    public static final class Serializer extends ValueSerializer<Atmospheres> {
        @Override
        public void serialize(Atmospheres value, JsonGenerator g, SerializationContext ctxt) {
            g.writeStartObject();
            for (AtmosphereAxis axis : AtmosphereAxis.ordered()) {
                g.writeName(axis.name());
                g.writeNumber(value.get(axis));
            }
            g.writeEndObject();
        }
    }

    /** {@link AnalyzedAtmospheres} 직렬화기: 미결 축은 {@code null} 로 <b>항상 포함</b>한다(키 누락 금지). */
    public static final class AnalyzedSerializer extends ValueSerializer<AnalyzedAtmospheres> {
        @Override
        public void serialize(AnalyzedAtmospheres value, JsonGenerator g, SerializationContext ctxt) {
            g.writeStartObject();
            for (AtmosphereAxis axis : AtmosphereAxis.ordered()) {
                g.writeName(axis.name());
                Integer v = value.get(axis);
                if (v == null) {
                    g.writeNull();
                } else {
                    g.writeNumber(v);
                }
            }
            g.writeEndObject();
        }
    }
}
