package team4.emotionmap.memory.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import team4.emotionmap.contracts.ai.AiAdapterException;
import team4.emotionmap.contracts.ai.AnalysisPort;
import team4.emotionmap.contracts.ai.AnalysisProvenance;
import team4.emotionmap.contracts.ai.AnalysisRequest;
import team4.emotionmap.contracts.ai.AnalysisResult;
import team4.emotionmap.contracts.dictionary.AnalyzedAtmospheres;
import team4.emotionmap.contracts.dictionary.AtmosphereAxis;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;

/**
 * {@link AnalysisPort} 의 <b>HTTP 구현</b>(C09/A04). 별도 AI FastAPI 서버의 {@code POST /ai/analyze} 를 호출한다.
 *
 * <p>계약 준수:
 * <ul>
 *   <li>AI 상류 실패(모델 오류·형식 불량·연결/시간 초과)는 예외가 아니라 {@link AnalysisResult#failed} 로 돌려준다.</li>
 *   <li>어댑터 설정 오류(RestClient 미주입 등)만 {@link AiAdapterException}(503).</li>
 *   <li>본문·응답 원문을 INFO 로그에 남기지 않는다(길이·상태만).</li>
 *   <li>축 null 은 그대로 null(작성자 입력 필요). 카테고리는 정확히 일치하는 코드만 인식한다.</li>
 * </ul>
 * provider=http 일 때만 활성. mock 은 {@code matchIfMissing=true} 라 자동으로 비활성화된다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = AiProperties.HTTP)
public class HttpAnalysisAdapter implements AnalysisPort {

    private final RestClient client;
    private final AnalysisProvenance fallbackProvenance;

    public HttpAnalysisAdapter(RestClient aiRestClient, AiProperties properties) {
        this.client = aiRestClient;
        this.fallbackProvenance = new AnalysisProvenance(
                properties.analysisModel() == null ? "ai-analyze" : properties.analysisModel(),
                properties.analysisPromptVersion() == null ? "http-v1" : properties.analysisPromptVersion(),
                AtmosphereAxis.DEFINITION_VERSION, PlaceCategoryCode.TAXONOMY_VERSION);
    }

    @Override
    @SuppressWarnings("unchecked")
    public AnalysisResult analyze(AnalysisRequest request) {
        long start = System.nanoTime();
        Map<String, Object> body;
        try {
            body = client.post()
                    .uri("/ai/analyze")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(Map.of("content", request.content()))
                    .retrieve()
                    .body(Map.class);
        } catch (RuntimeException e) {
            // 연결 실패·시간 초과·5xx 등 상류 문제 → 계약상 FAILED 결과(예외 아님).
            log.warn("ai analyze call failed: {}", e.getClass().getSimpleName());
            return AnalysisResult.failed(fallbackProvenance, "AI_ANALYZE_CALL_FAILED");
        }
        if (body == null) {
            return AnalysisResult.failed(fallbackProvenance, "AI_ANALYZE_EMPTY_RESPONSE");
        }

        AnalysisProvenance provenance = provenanceOf(body);
        // AI 가 자체 오류를 표시했거나 상태가 FAILED 면 실패 처리.
        Object error = body.get("error");
        String atmosphereStatus = str(body.get("atmosphereStatus"));
        if (error != null || "FAILED".equals(atmosphereStatus) && "FAILED".equals(str(body.get("categoryStatus")))) {
            log.info("ai analyze upstream failure durMs={}", durMs(start));
            return AnalysisResult.failed(provenance, "AI_UPSTREAM_FAILED");
        }

        try {
            AnalyzedAtmospheres atmospheres = atmospheresOf((Map<String, Object>) body.get("atmospheres"));
            List<PlaceCategoryCode> categories = categoriesOf(body.get("categories"));
            log.info("ai analyze ok durMs={} knownAxes={} categories={}",
                    durMs(start), atmospheres.knownCount(), categories.size());
            return AnalysisResult.of(atmospheres, categories, provenance);
        } catch (RuntimeException e) {
            // 형식 불량 응답은 상류 실패로 취급(우리 장애 아님).
            log.warn("ai analyze response mapping failed: {}", e.getClass().getSimpleName());
            return AnalysisResult.failed(provenance, "AI_ANALYZE_BAD_RESPONSE");
        }
    }

    private static AnalyzedAtmospheres atmospheresOf(Map<String, Object> m) {
        if (m == null) {
            return AnalyzedAtmospheres.ALL_UNKNOWN;
        }
        return new AnalyzedAtmospheres(
                axis(m.get("CROWD_LEVEL")), axis(m.get("SPATIAL_FEEL")),
                axis(m.get("COMPANY_FIT")), axis(m.get("STAY_STYLE")));
    }

    /** -1/+1 만 인정. 그 외(0·null·이상값)는 근거 없음(null). */
    private static Integer axis(Object v) {
        if (v instanceof Number n) {
            int i = n.intValue();
            if (i == AtmosphereAxis.POSITIVE || i == AtmosphereAxis.NEGATIVE) {
                return i;
            }
        }
        return null;
    }

    private static List<PlaceCategoryCode> categoriesOf(Object raw) {
        List<PlaceCategoryCode> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (out.size() == PlaceCategoryCode.MAX_PER_MEMORY) {
                    break;
                }
                PlaceCategoryCode.fromCode(str(o)).ifPresent(code -> {
                    if (!out.contains(code)) {
                        out.add(code);
                    }
                });
            }
        }
        return out;
    }

    private AnalysisProvenance provenanceOf(Map<String, Object> body) {
        String model = str(body.get("model"));
        String prompt = str(body.get("promptVersion"));
        return new AnalysisProvenance(
                model == null ? fallbackProvenance.model() : model,
                prompt == null ? fallbackProvenance.promptVersion() : prompt,
                AtmosphereAxis.DEFINITION_VERSION, PlaceCategoryCode.TAXONOMY_VERSION);
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static long durMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
