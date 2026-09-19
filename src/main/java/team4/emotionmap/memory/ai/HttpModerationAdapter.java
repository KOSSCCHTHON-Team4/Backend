package team4.emotionmap.memory.ai;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import team4.emotionmap.contracts.ai.AnalysisProvenance;
import team4.emotionmap.contracts.ai.ModerationPort;
import team4.emotionmap.contracts.ai.ModerationRequest;
import team4.emotionmap.contracts.ai.ModerationResult;
import team4.emotionmap.contracts.ai.ModerationVerdict;
import team4.emotionmap.contracts.dictionary.AtmosphereAxis;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;
import team4.emotionmap.contracts.media.SanitizedImage;

/**
 * {@link ModerationPort} 의 HTTP 구현(C09/A07). AI 서버 {@code POST /ai/moderate} 호출.
 *
 * <p>계약: 상류 실패는 {@link ModerationResult#error}(=ERROR verdict, 배달 승인 아님), 우리 장애만 예외.
 * 이미지가 있으면 재인코딩된 바이트를 {@code data:<mime>;base64,...} 로 실어 보낸다(외부 URL 금지).
 * 응답 {@code decision} 을 그대로 verdict 로 매핑한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = AiProperties.HTTP)
public class HttpModerationAdapter implements ModerationPort {

    private final RestClient client;
    private final AnalysisProvenance provenance;

    public HttpModerationAdapter(RestClient aiRestClient, AiProperties properties) {
        this.client = aiRestClient;
        this.provenance = new AnalysisProvenance(
                properties.moderationModel() == null ? "ai-moderate" : properties.moderationModel(),
                properties.moderationPromptVersion() == null ? "http-v1" : properties.moderationPromptVersion(),
                AtmosphereAxis.DEFINITION_VERSION, PlaceCategoryCode.TAXONOMY_VERSION);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ModerationResult moderate(ModerationRequest request) {
        long start = System.nanoTime();
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("content", request.content());
        if (request.hasImage()) {
            payload.put("imageDataUrl", toDataUrl(request.image()));
        }

        Map<String, Object> body;
        try {
            body = client.post().uri("/ai/moderate")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(payload).retrieve().body(Map.class);
        } catch (RuntimeException e) {
            log.warn("ai moderate call failed: {}", e.getClass().getSimpleName());
            return ModerationResult.error(provenance, "AI_MODERATE_CALL_FAILED");
        }
        if (body == null) {
            return ModerationResult.error(provenance, "AI_MODERATE_EMPTY_RESPONSE");
        }

        String decision = str(body.get("decision"));
        ModerationVerdict verdict = verdictOf(decision);
        log.info("ai moderate durMs={} decision={}", durMs(start), decision);
        if (verdict == ModerationVerdict.APPROVED) {
            return ModerationResult.approved(provenance);
        }
        if (verdict == ModerationVerdict.ERROR) {
            return ModerationResult.error(provenance, reasonCode(body, "AI_MODERATE_ERROR"));
        }
        // REVIEW_REQUIRED / REJECTED
        List<String> codes = reasonCodes(body);
        return new ModerationResult(verdict, codes.isEmpty() ? List.of(verdict.name()) : codes, provenance);
    }

    private static ModerationVerdict verdictOf(String decision) {
        if (decision == null) {
            return ModerationVerdict.ERROR;
        }
        return switch (decision) {
            case "APPROVED" -> ModerationVerdict.APPROVED;
            case "REVIEW_REQUIRED" -> ModerationVerdict.REVIEW_REQUIRED;
            case "REJECTED" -> ModerationVerdict.REJECTED;
            default -> ModerationVerdict.ERROR;
        };
    }

    /** 응답의 categories(짧은 코드) 만 reasonCodes 로 옮긴다. 본문 인용(reason 문장)은 넣지 않는다. */
    private static List<String> reasonCodes(Map<String, Object> body) {
        Object cats = body.get("categories");
        if (cats instanceof List<?> list) {
            return list.stream().map(HttpModerationAdapter::str).filter(s -> s != null && !s.isBlank()).toList();
        }
        return List.of();
    }

    private static String reasonCode(Map<String, Object> body, String fallback) {
        List<String> codes = reasonCodes(body);
        return codes.isEmpty() ? fallback : codes.get(0);
    }

    private static String toDataUrl(SanitizedImage image) {
        String base64 = Base64.getEncoder().encodeToString(image.bytes());
        return "data:" + image.mediaType().mimeType() + ";base64," + base64;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static long durMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
