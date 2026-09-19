package team4.emotionmap.memory.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import team4.emotionmap.contracts.dictionary.AnalyzedAtmospheres;

/**
 * {@code POST /v1/memories/analyze} 응답. API_SPEC 8.8 의 필수 키
 * ({@code atmospheres, categories, categoryStatus, atmosphereStatus, analysisToken, expiresAt, warnings}) +
 * 기획 §8 부가 출력({@code evidence, tags, categoryConfidence, categorySource, maskedContent, piiFound, safe, unsafeReason}).
 *
 * <p>{@code categoryStatus} 는 공개 API 값(CLASSIFIED/UNCLASSIFIED/FAILED)이다. 마스킹이 있으면 FE 는
 * {@code maskedContent} 를 저장 본문으로 제출할 수 있고, 서버는 원문·마스킹 본문 둘 다 같은 토큰으로 인정한다.
 */
public record AnalyzeResponse(
        AnalyzedAtmospheres atmospheres,
        List<String> categories,
        String categoryStatus,
        String atmosphereStatus,
        String analysisToken,
        Instant expiresAt,
        List<String> warnings,
        Map<String, String> evidence,
        List<String> tags,
        Double categoryConfidence,
        String categorySource,
        String maskedContent,
        Boolean piiFound,
        Boolean safe,
        String unsafeReason
) {
    public static final String WARN_ATMOSPHERE_NEEDS_INPUT = "ATMOSPHERE_NEEDS_INPUT";
    public static final String WARN_CATEGORY_UNCLASSIFIED = "CATEGORY_UNCLASSIFIED";
    public static final String WARN_ANALYSIS_FAILED = "ANALYSIS_FAILED";
    public static final String WARN_CATEGORY_LOW_CONFIDENCE = "CATEGORY_LOW_CONFIDENCE";
    public static final String WARN_CONTENT_UNSAFE = "CONTENT_UNSAFE";
    public static final String WARN_PII_MASKED = "PII_MASKED";
}
