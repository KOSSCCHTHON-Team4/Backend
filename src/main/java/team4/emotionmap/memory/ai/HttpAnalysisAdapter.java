package team4.emotionmap.memory.ai;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import team4.emotionmap.contracts.ai.AiAdapterException;
import team4.emotionmap.contracts.ai.AnalysisEnrichment;
import team4.emotionmap.contracts.ai.AnalysisPort;
import team4.emotionmap.contracts.ai.AnalysisProvenance;
import team4.emotionmap.contracts.ai.AnalysisRequest;
import team4.emotionmap.contracts.ai.AnalysisResult;
import team4.emotionmap.contracts.dictionary.AnalyzedAtmospheres;
import team4.emotionmap.contracts.dictionary.AtmosphereAxis;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;
import team4.emotionmap.contracts.memory.AtmosphereAnalysisStatus;
import team4.emotionmap.contracts.memory.CategoryAnalysisStatus;
import team4.emotionmap.platform.web.json.StrictJson;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link AnalysisPort} 의 <b>HTTP 구현</b>(C09/A04). 별도 AI FastAPI 서버의 {@code POST /ai/analyze} 를 호출한다.
 *
 * <p>계약 준수:
 * <ul>
 *   <li>AI 상류 실패(모델 오류·형식 불량·연결/시간 초과)는 예외가 아니라 {@link AnalysisResult#failed} 로 돌려준다.</li>
 *   <li>어댑터 설정 오류(RestClient 미주입 등)만 {@link AiAdapterException}(503).</li>
 *   <li>본문·응답 원문을 INFO 로그에 남기지 않는다(길이·상태만).</li>
 *   <li>원문 JSON stream 을 공통 strict mapper 로 읽어 네 축·카테고리·v2 metadata 를 모두 검증한다.</li>
 * </ul>
 * provider=http 일 때만 활성. mock 은 {@code matchIfMissing=true} 라 자동으로 비활성화된다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = AiProperties.HTTP)
public class HttpAnalysisAdapter implements AnalysisPort {

    private static final JsonMapper JSON = StrictJson.mapper();

    private final RestClient client;
    private final AnalysisProvenance fallbackProvenance;

    /**
     * AX-AI-WIRE-v2 flat response. Snake-case component names deliberately mirror the only accepted enrichment
     * keys; there are no camel-case aliases.
     */
    private record ProviderResponse(
            AnalyzedAtmospheres atmospheres,
            List<String> categories,
            String model,
            String promptVersion,
            int axisDefinitionVersion,
            int taxonomyVersion,
            String atmosphereStatus,
            String categoryStatus,
            String error,
            Map<String, String> evidence,
            List<String> tags,
            Double category_confidence,
            String category_source,
            String masked_content,
            Boolean pii_found,
            Boolean safe,
            String unsafe_reason
    ) {
    }

    public HttpAnalysisAdapter(RestClient aiRestClient, AiProperties properties) {
        this.client = aiRestClient;
        this.fallbackProvenance = new AnalysisProvenance(
                properties.analysisModel() == null ? "ai-analyze" : properties.analysisModel(),
                properties.analysisPromptVersion() == null ? "http-v1" : properties.analysisPromptVersion(),
                AtmosphereAxis.DEFINITION_VERSION, PlaceCategoryCode.TAXONOMY_VERSION);
    }

    @Override
    public AnalysisResult analyze(AnalysisRequest request) {
        long start = System.nanoTime();
        byte[] responseBytes;
        try {
            responseBytes = client.post()
                    .uri("/ai/analyze")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(requestBody(request))
                    .retrieve()
                    .body(byte[].class);
        } catch (RuntimeException e) {
            // 연결 실패·시간 초과·5xx 등 상류 문제 → 계약상 FAILED 결과(예외 아님).
            log.warn("ai analyze call failed: {}", e.getClass().getSimpleName());
            return AnalysisResult.failed(fallbackProvenance, "AI_ANALYZE_CALL_FAILED");
        }
        if (responseBytes == null || responseBytes.length == 0) {
            return AnalysisResult.failed(fallbackProvenance, "AI_ANALYZE_EMPTY_RESPONSE");
        }

        try {
            ProviderResponse response = JSON.readValue(responseBytes, ProviderResponse.class);
            AnalysisProvenance provenance = provenanceOf(response);
            AnalyzedAtmospheres atmospheres = requiredAtmospheres(response.atmospheres());
            List<PlaceCategoryCode> categories = categoriesOf(response.categories());
            AnalysisEnrichment enrichment = enrichmentOf(response);
            AtmosphereAnalysisStatus atmosphereStatus = atmosphereStatusOf(response.atmosphereStatus());
            CategoryAnalysisStatus categoryStatus = categoryStatusOf(response.categoryStatus());
            rejectNotRunStatus(atmosphereStatus, categoryStatus);

            if (response.error() != null) {
                validateErrorStatuses(atmosphereStatus, categoryStatus);
                log.info("ai analyze upstream failure durMs={}", durMs(start));
                return AnalysisResult.failed(provenance, "AI_UPSTREAM_FAILED");
            }
            if (categoryStatus == CategoryAnalysisStatus.FAILED) {
                if (atmosphereStatus != AtmosphereAnalysisStatus.FAILED
                        || !atmospheres.isEmpty() || !categories.isEmpty()) {
                    throw new IllegalArgumentException("inconsistent failed analysis response");
                }
                log.info("ai analyze upstream failure durMs={}", durMs(start));
                return AnalysisResult.failed(provenance, "AI_UPSTREAM_FAILED");
            }

            validateStatuses(atmospheres, categories, atmosphereStatus, categoryStatus);
            log.info("ai analyze ok durMs={} knownAxes={} categories={} pii={} safe={}",
                    durMs(start), atmospheres.knownCount(), categories.size(),
                    enrichment.piiFound(), enrichment.safe());
            return AnalysisResult.of(atmospheres, categories, provenance, enrichment);
        } catch (RuntimeException e) {
            // 형식 불량 응답은 축 일부를 null 로 salvage 하지 않고 전체 상류 실패로 취급한다.
            log.warn("ai analyze response mapping failed: {}", e.getClass().getSimpleName());
            return AnalysisResult.failed(fallbackProvenance, "AI_ANALYZE_BAD_RESPONSE");
        }
    }

    /** AX-AI-WIRE-v2: content, local axis/taxonomy version, 선택 naver_category. */
    private static Map<String, Object> requestBody(AnalysisRequest request) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("content", request.content());
        body.put("axisDefinitionVersion", request.axisDefinitionVersion());
        body.put("taxonomyVersion", request.taxonomyVersion());
        if (request.naverCategory() != null) {
            body.put("naver_category", request.naverCategory());
        }
        return body;
    }

    private static AnalyzedAtmospheres requiredAtmospheres(AnalyzedAtmospheres atmospheres) {
        if (atmospheres == null) {
            throw new IllegalArgumentException("missing atmospheres");
        }
        return atmospheres;
    }

    private static List<PlaceCategoryCode> categoriesOf(List<String> rawCodes) {
        if (rawCodes == null || rawCodes.size() > PlaceCategoryCode.MAX_PER_MEMORY) {
            throw new IllegalArgumentException("categories");
        }
        List<PlaceCategoryCode> categories = new ArrayList<>(rawCodes.size());
        EnumSet<PlaceCategoryCode> seen = EnumSet.noneOf(PlaceCategoryCode.class);
        for (String rawCode : rawCodes) {
            PlaceCategoryCode code = PlaceCategoryCode.fromCode(rawCode)
                    .orElseThrow(() -> new IllegalArgumentException("category"));
            if (!seen.add(code)) {
                throw new IllegalArgumentException("duplicate category");
            }
            categories.add(code);
        }
        if (seen.contains(PlaceCategoryCode.OTHER) && categories.size() != 1) {
            throw new IllegalArgumentException("OTHER must be exclusive");
        }
        return List.copyOf(categories);
    }

    private static AnalysisEnrichment enrichmentOf(ProviderResponse response) {
        return new AnalysisEnrichment(response.evidence(), response.tags(), response.category_confidence(),
                response.category_source(), response.masked_content(), response.pii_found(), response.safe(),
                response.unsafe_reason());
    }

    private static AnalysisProvenance provenanceOf(ProviderResponse response) {
        if (response.axisDefinitionVersion() != AtmosphereAxis.DEFINITION_VERSION
                || response.taxonomyVersion() != PlaceCategoryCode.TAXONOMY_VERSION) {
            throw new IllegalArgumentException("unverified provider metadata version");
        }
        return new AnalysisProvenance(requiredNonBlank(response.model(), "model"),
                requiredNonBlank(response.promptVersion(), "promptVersion"),
                response.axisDefinitionVersion(), response.taxonomyVersion());
    }

    private static String requiredNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field);
        }
        return value;
    }

    private static AtmosphereAnalysisStatus atmosphereStatusOf(String raw) {
        return raw == null ? null : AtmosphereAnalysisStatus.valueOf(raw);
    }

    private static CategoryAnalysisStatus categoryStatusOf(String raw) {
        return raw == null ? null : CategoryAnalysisStatus.valueOf(raw);
    }

    private static void rejectNotRunStatus(AtmosphereAnalysisStatus atmosphereStatus,
                                           CategoryAnalysisStatus categoryStatus) {
        if (atmosphereStatus == AtmosphereAnalysisStatus.NOT_RUN
                || categoryStatus == CategoryAnalysisStatus.NOT_RUN) {
            throw new IllegalArgumentException("analysis response status");
        }
    }

    private static void validateErrorStatuses(AtmosphereAnalysisStatus atmosphereStatus,
                                              CategoryAnalysisStatus categoryStatus) {
        if ((atmosphereStatus != null && atmosphereStatus != AtmosphereAnalysisStatus.FAILED)
                || (categoryStatus != null && categoryStatus != CategoryAnalysisStatus.FAILED)) {
            throw new IllegalArgumentException("error response status");
        }
    }

    private static void validateStatuses(AnalyzedAtmospheres atmospheres, List<PlaceCategoryCode> categories,
                                         AtmosphereAnalysisStatus atmosphereStatus,
                                         CategoryAnalysisStatus categoryStatus) {
        if (atmosphereStatus != null && atmosphereStatus != AnalysisResult.statusFor(atmospheres)) {
            throw new IllegalArgumentException("atmosphere status");
        }
        CategoryAnalysisStatus expectedCategoryStatus = categories.isEmpty()
                ? CategoryAnalysisStatus.INSUFFICIENT : CategoryAnalysisStatus.SUCCEEDED;
        if (categoryStatus != null && categoryStatus != expectedCategoryStatus) {
            throw new IllegalArgumentException("category status");
        }
    }

    private static long durMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
