package team4.emotionmap.memory.analysis;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import team4.emotionmap.contracts.ai.AnalysisEnrichment;
import team4.emotionmap.contracts.ai.AnalysisProvenance;
import team4.emotionmap.contracts.ai.AnalysisResult;
import team4.emotionmap.contracts.dictionary.AnalyzedAtmospheres;
import team4.emotionmap.contracts.dictionary.AtmosphereAxis;
import team4.emotionmap.contracts.dictionary.CategorySelection;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;
import team4.emotionmap.contracts.memory.AtmosphereAnalysisStatus;
import team4.emotionmap.contracts.memory.CategoryAnalysisStatus;
import team4.emotionmap.contracts.signing.ContentHash;

/**
 * analysisToken 이 담는 확인값(API_SPEC 4.1 + 기획 §8 부가 출력). 사용자 귀속·본문 해시·AI 제안·실행 상태·
 * 모델/프롬프트/사전 버전·만료, 그리고 근거·태그·카테고리 신뢰도·안전/개인정보 판정.
 *
 * <p><b>본문 원문·마스킹 본문·이메일은 넣지 않는다</b>(해시만). 개인정보가 마스킹된 경우 FE 는 분석 응답의
 * {@code maskedContent} 를 그대로 저장 본문으로 제출할 수 있으며, 서버는 원문 해시 또는 마스킹 해시 중 하나와 일치하면
 * 같은 분석 결과로 인정한다. 영구 저장하지 않는다.
 */
public record AnalysisReceipt(
        UUID userId,
        String contentSha256,
        String maskedSha256,
        AnalyzedAtmospheres atmospheres,
        List<PlaceCategoryCode> categories,
        AtmosphereAnalysisStatus atmosphereStatus,
        CategoryAnalysisStatus categoryStatus,
        AnalysisProvenance provenance,
        AnalysisEnrichment enrichment,
        Instant expiresAt
) {
    public AnalysisReceipt {
        Objects.requireNonNull(userId);
        Objects.requireNonNull(contentSha256);
        Objects.requireNonNull(atmospheres);
        Objects.requireNonNull(atmosphereStatus);
        Objects.requireNonNull(categoryStatus);
        Objects.requireNonNull(provenance);
        Objects.requireNonNull(expiresAt);
        categories = CategorySelection.requireValid(Objects.requireNonNull(categories));
        enrichment = enrichment == null ? AnalysisEnrichment.NONE : enrichment;
        requireCurrentProvenance(provenance);
        requireConsistentAnalysisState(atmospheres, categories, atmosphereStatus, categoryStatus);
    }

    private static void requireCurrentProvenance(AnalysisProvenance provenance) {
        if (provenance.axisDefinitionVersion() != AtmosphereAxis.DEFINITION_VERSION
                || provenance.taxonomyVersion() != PlaceCategoryCode.TAXONOMY_VERSION) {
            throw new IllegalArgumentException("analysis receipt requires current axis and taxonomy versions");
        }
    }

    private static void requireConsistentAnalysisState(AnalyzedAtmospheres atmospheres,
                                                       List<PlaceCategoryCode> categories,
                                                       AtmosphereAnalysisStatus atmosphereStatus,
                                                       CategoryAnalysisStatus categoryStatus) {
        if (atmosphereStatus != AnalysisResult.statusFor(atmospheres)) {
            throw new IllegalArgumentException("atmosphere status does not match known axis count");
        }
        switch (categoryStatus) {
            case SUCCEEDED -> {
                if (categories.isEmpty()) {
                    throw new IllegalArgumentException("successful category analysis requires categories");
                }
            }
            case INSUFFICIENT, FAILED -> {
                if (!categories.isEmpty()) {
                    throw new IllegalArgumentException("empty categories required for non-successful analysis");
                }
            }
            case NOT_RUN -> throw new IllegalArgumentException("analysis receipt cannot represent NOT_RUN");
        }
    }

    public static AnalysisReceipt from(UUID userId, String content, AnalysisResult result, Instant expiresAt) {
        AnalysisEnrichment e = result.enrichment();
        String maskedHash = e.hasMaskedContent(content) ? ContentHash.sha256Hex(e.maskedContent()) : null;
        // 토큰에는 마스킹 본문 대신 해시만 싣는다.
        AnalysisEnrichment withoutText = new AnalysisEnrichment(e.evidence(), e.tags(), e.categoryConfidence(),
                e.categorySource(), null, e.piiFound(), e.safe(), e.unsafeReason());
        return new AnalysisReceipt(userId, ContentHash.sha256Hex(content), maskedHash, result.atmospheres(),
                result.categories(), result.atmosphereStatus(), result.categoryStatus(),
                result.provenance(), withoutText, expiresAt);
    }

    /** 저장할 본문이 분석한 본문(원문) 또는 AI 마스킹 본문과 같은가(정규화 없이 원문 비교). */
    public boolean matchesContent(String content) {
        return ContentHash.matches(content, contentSha256)
                || (maskedSha256 != null && ContentHash.matches(content, maskedSha256));
    }

    /** 제출 본문이 마스킹 본문 쪽과 일치했는가(= 개인정보가 제거된 본문을 저장). */
    public boolean isMaskedSubmission(String content) {
        return maskedSha256 != null && ContentHash.matches(content, maskedSha256);
    }
}
