package team4.emotionmap.platform.web.json;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
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
 * <p>원문 JSON number 만 받아 {@link AtmosphereAxis#parseJsonNumber(String)} 로 lexical 범위를
 * binary64 변환 전에 확인한다. 문자열·boolean·배열·객체는 강제 변환하지 않는다. 최종 축은 null 을
 * 거절하고 분석 축만 개별 null 을 미결로 보존한다. 두 역직렬화기는 키·중복·필수 축·숫자 검사를
 * {@link #readAxes(JsonParser, String, boolean)} 에서 공유한다.
 */
public final class AtmospheresJson {

    private static final String DEFAULT_PROPERTY = "atmospheres";

    private AtmospheresJson() {
    }

    /** {@link Atmospheres} 역직렬화기(요청용). */
    public static final class Deserializer extends ValueDeserializer<Atmospheres> {
        @Override
        public Atmospheres deserialize(JsonParser p, DeserializationContext ctxt) {
            return Atmospheres.fromMap(readAxes(p, prefixOf(p), false));
        }

        /** 속성 자체가 {@code null} 이면 축 4개 모두 REQUIRED. */
        @Override
        public Object getNullValue(DeserializationContext ctxt) {
            throw requiredObject(ctxt);
        }
    }

    /** {@link AnalyzedAtmospheres} 역직렬화기(AI 응답용). 개별 null 은 미결 축이다. */
    public static final class AnalyzedDeserializer extends ValueDeserializer<AnalyzedAtmospheres> {
        @Override
        public AnalyzedAtmospheres deserialize(JsonParser p, DeserializationContext ctxt) {
            EnumMap<AtmosphereAxis, Double> values = readAxes(p, prefixOf(p), true);
            return new AnalyzedAtmospheres(
                    values.get(AtmosphereAxis.CROWD_LEVEL),
                    values.get(AtmosphereAxis.SPATIAL_FEEL),
                    values.get(AtmosphereAxis.COMPANY_FIT),
                    values.get(AtmosphereAxis.STAY_STYLE));
        }

        /** 분석 객체 자체의 null 은 축 미결을 뜻하지 않는다. */
        @Override
        public Object getNullValue(DeserializationContext ctxt) {
            throw requiredObject(ctxt);
        }
    }

    private static EnumMap<AtmosphereAxis, Double> readAxes(JsonParser p, String prefix, boolean allowNullValues) {
        if (p.currentToken() == null) {
            // 잘린 JSON: 의미 검증(422)이 아니라 구문 오류(400 INVALID_JSON)다.
            throw new StreamReadException(p, "Unexpected end-of-input while reading atmospheres");
        }
        if (!p.isExpectedStartObjectToken()) {
            p.skipChildren();
            throw ContractError.of(ErrorCode.INVALID_ATMOSPHERES, FieldError.invalid(prefix));
        }

        EnumMap<AtmosphereAxis, Double> values = new EnumMap<>(AtmosphereAxis.class);
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
                if (allowNullValues) {
                    values.put(axis, null);
                } else {
                    errors.add(FieldError.required(path));
                }
                continue;
            }
            if (token != JsonToken.VALUE_NUMBER_INT && token != JsonToken.VALUE_NUMBER_FLOAT) {
                errors.add(FieldError.invalid(path));
                p.skipChildren();
                continue;
            }
            try {
                values.put(axis, parseNumberToken(p));
            } catch (IllegalArgumentException e) {
                errors.add(FieldError.invalid(path));
            }
        }
        for (AtmosphereAxis axis : AtmosphereAxis.ordered()) {
            if (!touched.contains(axis)) {
                errors.add(FieldError.required(prefix + "." + axis.name()));
            }
        }
        if (!errors.isEmpty()) {
            throw ContractError.of(ErrorCode.INVALID_ATMOSPHERES, errors);
        }
        return values;
    }

    private static double parseNumberToken(JsonParser p) {
        if (p.getStringLength() > AtmosphereAxis.MAX_JSON_NUMBER_LENGTH) {
            throw new StreamReadException(p, "Atmosphere axis number exceeds the maximum length");
        }
        return AtmosphereAxis.parseJsonNumber(p.getString());
    }

    private static String prefixOf(JsonParser p) {
        return p.currentName() == null ? DEFAULT_PROPERTY : p.currentName();
    }

    private static ContractError requiredObject(DeserializationContext ctxt) {
        String prefix = ctxt.getParser() != null ? prefixOf(ctxt.getParser()) : DEFAULT_PROPERTY;
        return ContractError.of(ErrorCode.INVALID_ATMOSPHERES, FieldError.required(prefix));
    }

    private static AtmosphereAxis axisOf(String name) {
        for (AtmosphereAxis axis : AtmosphereAxis.ordered()) {
            if (axis.name().equals(name)) {
                return axis;
            }
        }
        return null;
    }

    /** {@link Atmospheres} 직렬화기(응답용): 키는 축 코드, 값은 canonical double 이다. */
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
                Double v = value.get(axis);
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
