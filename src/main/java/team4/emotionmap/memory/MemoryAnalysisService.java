package team4.emotionmap.memory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import team4.emotionmap.contracts.ai.AnalysisEnrichment;
import team4.emotionmap.contracts.ai.AnalysisPort;
import team4.emotionmap.contracts.ai.AnalysisRequest;
import team4.emotionmap.contracts.ai.AnalysisResult;
import team4.emotionmap.contracts.config.MatchingProperties;
import team4.emotionmap.contracts.config.ServiceConfigSource;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.memory.AtmosphereAnalysisStatus;
import team4.emotionmap.contracts.memory.CategoryAnalysisStatus;
import team4.emotionmap.contracts.validation.TextRules;
import team4.emotionmap.memory.ai.AiProperties;
import team4.emotionmap.memory.analysis.AnalysisReceipt;
import team4.emotionmap.memory.analysis.AnalysisReceiptCodec;
import team4.emotionmap.memory.dto.AnalyzeRequest;
import team4.emotionmap.memory.dto.AnalyzeResponse;
import team4.emotionmap.place.NaverCategoryMapper;

/**
 * A04 {@code POST /v1/memories/analyze} (기획 §1 앞단 구조화). 흐름:
 * 본문 검증 → AnalysisPort(본문 + 네이버 카테고리) → 카테고리 출처 결정(네이버 매핑 우선, AI 는 신뢰도 하한 적용)
 * → analysisToken 서명 → 응답. DB 에 쓰지 않는다. AI 상류 실패는 200 + FAILED + null 축(수동 입력 경로).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryAnalysisService {

    private final AnalysisPort analysisPort;
    private final AnalysisReceiptCodec receiptCodec;
    private final ServiceConfigSource serviceConfig;
    private final MatchingProperties matching;
    private final AiProperties aiProperties;
    private final Clock clock;

    public AnalyzeResponse analyze(UUID userId, AnalyzeRequest request) {
        String content = TextRules.requireContent(request.content(),
                serviceConfig.limits().memoryContentMaxCodePoints(), "content", ErrorCode.VALIDATION_ERROR);
        Duration timeout = aiProperties.timeout() == null ? Duration.ofSeconds(8) : aiProperties.timeout();

        AnalysisResult raw = analysisPort.analyze(AnalysisRequest.of(content, request.naverCategory(), timeout));
        AnalysisResult result = applyCategoryPolicy(raw, request.naverCategory());

        Instant now = clock.instant();
        Instant expiresAt = now.plusSeconds(serviceConfig.limits().analysisTtlSeconds());
        AnalysisReceipt receipt = AnalysisReceipt.from(userId, content, result, expiresAt);
        String token = receiptCodec.encode(receipt);

        AnalysisEnrichment e = result.enrichment();
        List<String> warnings = warningsOf(result, content);
        log.info("memory analyze user={} atmosphereStatus={} categoryStatus={} categories={} safe={} pii={}",
                userId, result.atmosphereStatus(), result.categoryStatus(), result.categories().size(),
                e.safe(), e.piiFound());
        return new AnalyzeResponse(
                result.atmospheres(),
                result.categories().stream().map(Enum::name).toList(),
                publicCategoryStatus(result.categoryStatus()),
                result.atmosphereStatus().name(),
                token,
                expiresAt,
                warnings,
                e.evidence(),
                e.tags(),
                e.categoryConfidence(),
                e.categorySource(),
                e.hasMaskedContent(content) ? e.maskedContent() : null,
                e.piiFound(),
                e.safe(),
                e.unsafeReason());
    }

    /**
     * 기획 §5: 네이버 등록 장소는 매핑 테이블이 우선(source=naver). 미등록 장소는 AI 추론이며
     * 신뢰도 &lt; 하한이면 제안을 OTHER 로 바꾼다(사용자 수정 가능). 상류 실패(FAILED)는 그대로 둔다 — 실패를 OTHER 로 채우지 않는다.
     */
    AnalysisResult applyCategoryPolicy(AnalysisResult result, String naverCategory) {
        AnalysisEnrichment e = result.enrichment();
        PlaceCategoryCode naver = NaverCategoryMapper.map(naverCategory).orElse(null);
        if (naver != null) {
            List<PlaceCategoryCode> cats = new ArrayList<>();
            cats.add(naver);
            for (PlaceCategoryCode c : result.categories()) {
                if (!cats.contains(c) && cats.size() < PlaceCategoryCode.MAX_PER_MEMORY) {
                    cats.add(c);
                }
            }
            AnalysisEnrichment enriched = new AnalysisEnrichment(e.evidence(), e.tags(), 1.0, "naver",
                    e.maskedContent(), e.piiFound(), e.safe(), e.unsafeReason());
            return new AnalysisResult(result.atmospheres(), cats, CategoryAnalysisStatus.SUCCEEDED,
                    result.atmosphereStatus(), result.provenance(), null, enriched);
        }
        if (result.isUpstreamFailure() || result.categoryStatus() != CategoryAnalysisStatus.SUCCEEDED) {
            return result;
        }
        Double confidence = e.categoryConfidence();
        if (confidence != null && confidence < matching.categoryConfidenceThreshold()) {
            AnalysisEnrichment enriched = new AnalysisEnrichment(e.evidence(), e.tags(), confidence, "ai",
                    e.maskedContent(), e.piiFound(), e.safe(), e.unsafeReason());
            return new AnalysisResult(result.atmospheres(), List.of(PlaceCategoryCode.OTHER),
                    CategoryAnalysisStatus.SUCCEEDED, result.atmosphereStatus(), result.provenance(), null, enriched);
        }
        if (e.categorySource() == null) {
            return result.withEnrichment(new AnalysisEnrichment(e.evidence(), e.tags(), confidence, "ai",
                    e.maskedContent(), e.piiFound(), e.safe(), e.unsafeReason()));
        }
        return result;
    }

    static String publicCategoryStatus(CategoryAnalysisStatus status) {
        return switch (status) {
            case SUCCEEDED -> "CLASSIFIED";
            case INSUFFICIENT -> "UNCLASSIFIED";
            case FAILED, NOT_RUN -> "FAILED";
        };
    }

    private List<String> warningsOf(AnalysisResult result, String content) {
        List<String> warnings = new ArrayList<>();
        if (result.isUpstreamFailure()) {
            warnings.add(AnalyzeResponse.WARN_ANALYSIS_FAILED);
        } else {
            if (result.atmosphereStatus() != AtmosphereAnalysisStatus.SUCCEEDED) {
                warnings.add(AnalyzeResponse.WARN_ATMOSPHERE_NEEDS_INPUT);
            }
            if (result.categoryStatus() == CategoryAnalysisStatus.INSUFFICIENT) {
                warnings.add(AnalyzeResponse.WARN_CATEGORY_UNCLASSIFIED);
            }
        }
        AnalysisEnrichment e = result.enrichment();
        if (e.categoryConfidence() != null && e.categoryConfidence() < matching.categoryConfidenceThreshold()
                && result.categories().contains(PlaceCategoryCode.OTHER)) {
            warnings.add(AnalyzeResponse.WARN_CATEGORY_LOW_CONFIDENCE);
        }
        if (e.isUnsafe()) {
            warnings.add(AnalyzeResponse.WARN_CONTENT_UNSAFE);
        }
        if (Boolean.TRUE.equals(e.piiFound()) || e.hasMaskedContent(content)) {
            warnings.add(AnalyzeResponse.WARN_PII_MASKED);
        }
        return warnings;
    }
}
