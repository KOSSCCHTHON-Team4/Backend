package team4.emotionmap.memory.analysis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import team4.emotionmap.contracts.ai.AnalysisEnrichment;
import team4.emotionmap.contracts.ai.AnalysisProvenance;
import team4.emotionmap.contracts.dictionary.AnalyzedAtmospheres;
import team4.emotionmap.contracts.dictionary.AtmosphereAxis;
import team4.emotionmap.contracts.dictionary.CategorySelection;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.memory.AtmosphereAnalysisStatus;
import team4.emotionmap.contracts.memory.CategoryAnalysisStatus;
import team4.emotionmap.contracts.signing.SignedClaims;
import team4.emotionmap.contracts.signing.SignedValueCodec;
import team4.emotionmap.contracts.signing.SignedValuePurpose;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link AnalysisReceipt} ↔ analysisToken 문자열. 서명·만료·용도·사용자 검증은 {@link SignedValueCodec} 가,
 * 본문 대응은 {@link #verify} 의 호출자가 {@code content} 를 넘겨 여기서 확인한다.
 *
 * <p>오류: 위변조·다른 사용자·구 payload/형식 → ANALYSIS_TOKEN_INVALID(422), 만료 → ANALYSIS_TOKEN_EXPIRED(422),
 * 본문 불일치 → ANALYSIS_CONTENT_MISMATCH(422). 구 payload 는 자동 이행하지 않으며 다시 분석해야 한다.
 */
@Component
public class AnalysisReceiptCodec {

    /** v3: axes-v2 canonical binary64 claim. 이전 payload 는 수락하지 않는다. */
    static final int PAYLOAD_VERSION = 3;

    private static final String K_HASH = "h";
    private static final String K_MASKED_HASH = "mh";
    /** {@code axes-v2:<field>,<field>,<field>,<field>}; 순서는 AtmosphereAxis.ordered(). */
    private static final String K_AXES = "a";
    private static final String K_CATS = "c";       // 쉼표 구분 코드
    private static final String K_AXIS_STATUS = "as";
    private static final String K_CAT_STATUS = "cs";
    private static final String K_MODEL = "m";
    private static final String K_PROMPT = "pv";
    private static final String K_AXIS_DEF = "ad";
    private static final String K_TAXONOMY = "tx";
    private static final String K_EVIDENCE = "ev";  // JSON object 문자열
    private static final String K_TAGS = "tg";      // JSON array 문자열
    private static final String K_CAT_CONF = "cc";
    private static final String K_CAT_SRC = "csrc";
    private static final String K_SAFE = "sf";      // "1"/"0"
    private static final String K_PII = "pii";      // "1"/"0"
    private static final String K_UNSAFE_REASON = "ur";

    private static final String AXES_V2_PREFIX = "axes-v2:";
    private static final int AXIS_COUNT = 4;
    private static final int AXIS_BITS_HEX_LENGTH = 16;
    private static final int AXES_V2_MAX_LENGTH = AXES_V2_PREFIX.length()
            + AXIS_COUNT * AXIS_BITS_HEX_LENGTH + (AXIS_COUNT - 1);
    private static final char[] LOWER_HEX = "0123456789abcdef".toCharArray();

    private static final int MAX_EVIDENCE_VALUE = 120;
    private static final int MAX_TAGS = 10;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<LinkedHashMap<String, String>> STRING_MAP = new TypeReference<>() { };
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final SignedValueCodec codec;

    public AnalysisReceiptCodec(SignedValueCodec codec) {
        this.codec = codec;
    }

    public String encode(AnalysisReceipt receipt) {
        Map<String, String> claims = new LinkedHashMap<>();
        claims.put(K_HASH, receipt.contentSha256());
        if (receipt.maskedSha256() != null) {
            claims.put(K_MASKED_HASH, receipt.maskedSha256());
        }
        claims.put(K_AXES, encodeAxes(receipt.atmospheres()));
        claims.put(K_CATS, String.join(",", receipt.categories().stream().map(Enum::name).toList()));
        claims.put(K_AXIS_STATUS, receipt.atmosphereStatus().name());
        claims.put(K_CAT_STATUS, receipt.categoryStatus().name());
        claims.put(K_MODEL, receipt.provenance().model());
        claims.put(K_PROMPT, receipt.provenance().promptVersion());
        claims.put(K_AXIS_DEF, Integer.toString(receipt.provenance().axisDefinitionVersion()));
        claims.put(K_TAXONOMY, Integer.toString(receipt.provenance().taxonomyVersion()));

        AnalysisEnrichment e = receipt.enrichment();
        if (!e.evidence().isEmpty()) {
            Map<String, String> bounded = new LinkedHashMap<>();
            e.evidence().forEach((k, v) -> bounded.put(k,
                    v.length() > MAX_EVIDENCE_VALUE ? v.substring(0, MAX_EVIDENCE_VALUE) : v));
            claims.put(K_EVIDENCE, JSON.writeValueAsString(bounded));
        }
        if (!e.tags().isEmpty()) {
            claims.put(K_TAGS, JSON.writeValueAsString(e.tags().stream().limit(MAX_TAGS).toList()));
        }
        if (e.categoryConfidence() != null) {
            claims.put(K_CAT_CONF, Double.toString(e.categoryConfidence()));
        }
        if (e.categorySource() != null) {
            claims.put(K_CAT_SRC, e.categorySource());
        }
        if (e.safe() != null) {
            claims.put(K_SAFE, e.safe() ? "1" : "0");
        }
        if (e.piiFound() != null) {
            claims.put(K_PII, e.piiFound() ? "1" : "0");
        }
        if (e.unsafeReason() != null) {
            claims.put(K_UNSAFE_REASON, e.unsafeReason());
        }
        return codec.sign(new SignedClaims(SignedValuePurpose.ANALYSIS_TOKEN, receipt.userId(),
                PAYLOAD_VERSION, receipt.expiresAt(), claims));
    }

    /**
     * @param token   요청의 analysisToken (null 아님)
     * @param userId  로그인 사용자
     * @param content 저장할 본문 원문(또는 AI 마스킹 본문)
     */
    public AnalysisReceipt verify(String token, UUID userId, String content, Instant now) {
        SignedClaims claims = codec.verify(token, SignedValuePurpose.ANALYSIS_TOKEN, userId, PAYLOAD_VERSION, now);
        AnalysisReceipt receipt;
        try {
            receipt = new AnalysisReceipt(
                    userId,
                    required(claims, K_HASH),
                    claims.claim(K_MASKED_HASH),
                    decodeAxes(required(claims, K_AXES)),
                    decodeCategories(required(claims, K_CATS)),
                    AtmosphereAnalysisStatus.valueOf(required(claims, K_AXIS_STATUS)),
                    CategoryAnalysisStatus.valueOf(required(claims, K_CAT_STATUS)),
                    new AnalysisProvenance(required(claims, K_MODEL), required(claims, K_PROMPT),
                            requiredCurrentVersion(claims, K_AXIS_DEF, AtmosphereAxis.DEFINITION_VERSION),
                            requiredCurrentVersion(claims, K_TAXONOMY, PlaceCategoryCode.TAXONOMY_VERSION)),
                    decodeEnrichment(claims),
                    claims.expiresAt());
        } catch (ContractError e) {
            throw e;
        } catch (RuntimeException e) {
            throw ContractError.of(ErrorCode.ANALYSIS_TOKEN_INVALID);
        }
        if (!receipt.matchesContent(content)) {
            throw ContractError.of(ErrorCode.ANALYSIS_CONTENT_MISMATCH);
        }
        return receipt;
    }

    private static AnalysisEnrichment decodeEnrichment(SignedClaims claims) {
        Map<String, String> evidence = claims.claim(K_EVIDENCE) == null
                ? Map.of() : JSON.readValue(claims.claim(K_EVIDENCE), STRING_MAP);
        List<String> tags = claims.claim(K_TAGS) == null
                ? List.of() : JSON.readValue(claims.claim(K_TAGS), STRING_LIST);
        Double conf = claims.claim(K_CAT_CONF) == null ? null : Double.parseDouble(claims.claim(K_CAT_CONF));
        Boolean safe = flag(claims.claim(K_SAFE));
        Boolean pii = flag(claims.claim(K_PII));
        return new AnalysisEnrichment(evidence, tags, conf, claims.claim(K_CAT_SRC), null, pii, safe,
                claims.claim(K_UNSAFE_REASON));
    }

    private static Boolean flag(String v) {
        if (v == null) {
            return null;
        }
        return switch (v) {
            case "1" -> Boolean.TRUE;
            case "0" -> Boolean.FALSE;
            default -> throw new IllegalArgumentException("flag");
        };
    }

    private static String required(SignedClaims claims, String key) {
        String v = claims.claim(key);
        if (v == null) {
            throw ContractError.of(ErrorCode.ANALYSIS_TOKEN_INVALID);
        }
        return v;
    }

    private static int requiredCurrentVersion(SignedClaims claims, String key, int expected) {
        if (!Integer.toString(expected).equals(required(claims, key))) {
            throw new IllegalArgumentException("analysis metadata version");
        }
        return expected;
    }

    static String encodeAxes(AnalyzedAtmospheres atmospheres) {
        StringBuilder encoded = new StringBuilder(AXES_V2_MAX_LENGTH);
        encoded.append(AXES_V2_PREFIX);
        for (int index = 0; index < AXIS_COUNT; index++) {
            if (index > 0) {
                encoded.append(',');
            }
            appendAxis(encoded, atmospheres.get(AtmosphereAxis.ordered().get(index)));
        }
        return encoded.toString();
    }

    private static void appendAxis(StringBuilder encoded, Double value) {
        if (value == null) {
            encoded.append('?');
            return;
        }
        long bits = Double.doubleToLongBits(AtmosphereAxis.requireValidValue(value));
        for (int shift = 60; shift >= 0; shift -= 4) {
            encoded.append(LOWER_HEX[(int) ((bits >>> shift) & 0xf)]);
        }
    }

    static AnalyzedAtmospheres decodeAxes(String encoded) {
        if (encoded == null || !encoded.startsWith(AXES_V2_PREFIX) || encoded.length() > AXES_V2_MAX_LENGTH) {
            throw new IllegalArgumentException("axes");
        }
        Double[] values = new Double[AXIS_COUNT];
        int fieldStart = AXES_V2_PREFIX.length();
        for (int index = 0; index < AXIS_COUNT; index++) {
            int fieldEnd = index == AXIS_COUNT - 1 ? encoded.length() : encoded.indexOf(',', fieldStart);
            if (fieldEnd < fieldStart) {
                throw new IllegalArgumentException("axes");
            }
            values[index] = decodeAxisField(encoded, fieldStart, fieldEnd);
            fieldStart = fieldEnd + 1;
        }
        return new AnalyzedAtmospheres(values[0], values[1], values[2], values[3]);
    }

    private static Double decodeAxisField(String encoded, int start, int end) {
        int length = end - start;
        if (length == 1 && encoded.charAt(start) == '?') {
            return null;
        }
        if (length != AXIS_BITS_HEX_LENGTH) {
            throw new IllegalArgumentException("axis bits");
        }
        long bits = 0L;
        for (int index = start; index < end; index++) {
            int hex = hexDigit(encoded.charAt(index));
            if (hex < 0) {
                throw new IllegalArgumentException("axis bits");
            }
            bits = (bits << 4) | hex;
        }
        double canonical = AtmosphereAxis.requireValidValue(Double.longBitsToDouble(bits));
        if (Double.doubleToLongBits(canonical) != bits) {
            throw new IllegalArgumentException("non-canonical axis bits");
        }
        return canonical;
    }

    private static int hexDigit(char value) {
        if (value >= '0' && value <= '9') {
            return value - '0';
        }
        return value >= 'a' && value <= 'f' ? value - 'a' + 10 : -1;
    }

    static List<PlaceCategoryCode> decodeCategories(String encoded) {
        if (encoded == null) {
            throw new IllegalArgumentException("category");
        }
        if (encoded.isEmpty()) {
            return List.of();
        }
        if (encoded.charAt(encoded.length() - 1) == ',') {
            throw new IllegalArgumentException("category");
        }
        List<PlaceCategoryCode> categories = new ArrayList<>();
        int start = 0;
        while (start < encoded.length()) {
            int comma = encoded.indexOf(',', start);
            int end = comma < 0 ? encoded.length() : comma;
            if (end == start) {
                throw new IllegalArgumentException("category");
            }
            categories.add(PlaceCategoryCode.fromCode(encoded.substring(start, end))
                    .orElseThrow(() -> new IllegalArgumentException("category")));
            start = end + 1;
        }
        return CategorySelection.requireValid(categories);
    }
}
