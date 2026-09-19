package team4.emotionmap.memory.analysis;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import team4.emotionmap.contracts.ai.AnalysisProvenance;
import team4.emotionmap.contracts.ai.AnalysisResult;
import team4.emotionmap.contracts.dictionary.AnalyzedAtmospheres;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;
import team4.emotionmap.contracts.memory.AtmosphereAnalysisStatus;
import team4.emotionmap.contracts.memory.CategoryAnalysisStatus;
import team4.emotionmap.contracts.signing.ContentHash;

/**
 * analysisToken 이 담는 확인값(API_SPEC 4.1). 사용자 귀속·본문 해시·AI 제안·실행 상태·모델/프롬프트/사전 버전·만료.
 * <b>본문 원문·이메일은 넣지 않는다.</b> 최종 저장 시 서명·사용자·본문 해시·만료를 검사하고, AI 제안과 최종값을 비교해
 * 축/카테고리별 AI/USER 출처를 서버가 정한다. 영구 저장하지 않는다.
 */
public record AnalysisReceipt(
        UUID userId,
        String contentSha256,
        AnalyzedAtmospheres atmospheres,
        List<PlaceCategoryCode> categories,
        AtmosphereAnalysisStatus atmosphereStatus,
        CategoryAnalysisStatus categoryStatus,
        AnalysisProvenance provenance,
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
        categories = List.copyOf(Objects.requireNonNull(categories));
    }

    public static AnalysisReceipt from(UUID userId, String content, AnalysisResult result, Instant expiresAt) {
        return new AnalysisReceipt(userId, ContentHash.sha256Hex(content), result.atmospheres(),
                result.categories(), result.atmosphereStatus(), result.categoryStatus(),
                result.provenance(), expiresAt);
    }

    /** 저장할 본문이 분석한 본문과 같은가(정규화 없이 원문 비교). */
    public boolean matchesContent(String content) {
        return ContentHash.matches(content, contentSha256);
    }
}
