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
 * <p>오류: 위변조·다른 사용자·형식 → ANALYSIS_TOKEN_INVALID(422), 만료 → ANALYSIS_TOKEN_EXPIRED(422),
 * 본문 불일치 → ANALYSIS_CONTENT_MISMATCH(422). 토큰 null 은 여기 오지 않는다(수동 저장 = NOT_RUN/USER).
 */
@Component
public class AnalysisReceiptCodec {

    /** 클레임 배치가 바뀌면 올린다. 다른 버전의 토큰은 INVALID. v2: 마스킹 해시·근거·태그·신뢰도·안전 판정 추가. */
    static final int PAYLOAD_VERSION = 2;

    private static final String K_HASH = "h";
    private static final String K_MASKED_HASH = "mh";
    private static final String K_AXES = "a";       // 4자리: 각 축 "-", "+", "?" (순서 = AtmosphereAxis.ordered)
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
            e.evidence().forEach((k, v) -> bounded.put(k, v.length() > MAX_EVIDENCE_VALUE ? v.substring(0, MAX_EVIDENCE_VALUE) : v));
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
                    decodeCategories(claims.claim(K_CATS)),
                    AtmosphereAnalysisStatus.valueOf(required(claims, K_AXIS_STATUS)),
                    CategoryAnalysisStatus.valueOf(required(claims, K_CAT_STATUS)),
                    new AnalysisProvenance(required(claims, K_MODEL), required(claims, K_PROMPT),
                            Integer.parseInt(required(claims, K_AXIS_DEF)),
                            Integer.parseInt(required(claims, K_TAXONOMY))),
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

    static String encodeAxes(AnalyzedAtmospheres a) {
        StringBuilder sb = new StringBuilder(4);
        for (AtmosphereAxis axis : AtmosphereAxis.ordered()) {
            Integer v = a.get(axis);
            sb.append(v == null ? '?' : (v == AtmosphereAxis.POSITIVE ? '+' : '-'));
        }
        return sb.toString();
    }

    static AnalyzedAtmospheres decodeAxes(String s) {
        if (s.length() != 4) {
            throw new IllegalArgumentException("axes");
        }
        Integer[] v = new Integer[4];
        for (int i = 0; i < 4; i++) {
            v[i] = switch (s.charAt(i)) {
                case '+' -> AtmosphereAxis.POSITIVE;
                case '-' -> AtmosphereAxis.NEGATIVE;
                case '?' -> null;
                default -> throw new IllegalArgumentException("axes");
            };
        }
        return new AnalyzedAtmospheres(v[0], v[1], v[2], v[3]);
    }

    static List<PlaceCategoryCode> decodeCategories(String s) {
        if (s == null || s.isEmpty()) {
            return List.of();
        }
        List<PlaceCategoryCode> list = new ArrayList<>();
        for (String code : s.split(",")) {
            list.add(PlaceCategoryCode.fromCode(code).orElseThrow(() -> new IllegalArgumentException("category")));
        }
        return list;
    }
}
