package team4.emotionmap.memory.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import team4.emotionmap.contracts.ai.PreferenceVerifyPort;
import team4.emotionmap.contracts.ai.VerifyRequest;
import team4.emotionmap.contracts.ai.VerifyResult;

/**
 * {@link PreferenceVerifyPort} HTTP 구현. 기획 §8 {@code POST /ai/verify}:
 * 요청 {@code {preference_text, reviews[]}} → 응답 {@code {fit, confidence, reason, evidence[]}}.
 * 연결 실패·형식 불량은 {@link VerifyResult#failed}(fit=false). 취향 문장·리뷰는 로그에 남기지 않는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = AiProperties.HTTP)
public class HttpPreferenceVerifyAdapter implements PreferenceVerifyPort {

    private final RestClient client;

    public HttpPreferenceVerifyAdapter(RestClient aiRestClient) {
        this.client = aiRestClient;
    }

    @Override
    @SuppressWarnings("unchecked")
    public VerifyResult verify(VerifyRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("preference_text", request.preferenceText());
        payload.put("reviews", request.reviews());
        Map<String, Object> body;
        try {
            body = client.post().uri("/ai/verify").contentType(MediaType.APPLICATION_JSON)
                    .body(payload).retrieve().body(Map.class);
        } catch (RuntimeException e) {
            log.warn("ai verify call failed: {}", e.getClass().getSimpleName());
            return VerifyResult.failed("AI_VERIFY_CALL_FAILED");
        }
        if (body == null || !(body.get("fit") instanceof Boolean fit)) {
            return VerifyResult.failed("AI_VERIFY_BAD_RESPONSE");
        }
        double confidence = body.get("confidence") instanceof Number n ? n.doubleValue() : (fit ? 0.5 : 0.0);
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        String reason = body.get("reason") == null ? null : body.get("reason").toString();
        List<String> evidence = new ArrayList<>();
        if (body.get("evidence") instanceof List<?> list) {
            list.stream().filter(o -> o != null).map(Object::toString).limit(10).forEach(evidence::add);
        }
        log.info("ai verify ok fit={} confidence={}", fit, confidence);
        return VerifyResult.of(fit, confidence, reason, evidence);
    }
}
