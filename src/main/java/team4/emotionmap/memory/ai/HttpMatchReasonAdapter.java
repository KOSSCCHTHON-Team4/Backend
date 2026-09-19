package team4.emotionmap.memory.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import team4.emotionmap.contracts.ai.MatchReasonPort;
import team4.emotionmap.contracts.ai.MatchReasonRequest;
import team4.emotionmap.contracts.ai.MatchReasonResult;

/**
 * {@link MatchReasonPort} HTTP 구현. 기획 §8 {@code POST /ai/match-reason}:
 * 요청 {@code {cards[], place_name, reviews[]}} → 응답 {@code {reason}}. 실패는 {@link MatchReasonResult#unavailable()}.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = AiProperties.HTTP)
public class HttpMatchReasonAdapter implements MatchReasonPort {

    private final RestClient client;

    public HttpMatchReasonAdapter(RestClient aiRestClient) {
        this.client = aiRestClient;
    }

    @Override
    @SuppressWarnings("unchecked")
    public MatchReasonResult explain(MatchReasonRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cards", request.cards());
        payload.put("place_name", request.placeName());
        payload.put("reviews", request.reviews());
        Map<String, Object> body;
        try {
            body = client.post().uri("/ai/match-reason").contentType(MediaType.APPLICATION_JSON)
                    .body(payload).retrieve().body(Map.class);
        } catch (RuntimeException e) {
            log.warn("ai match-reason call failed: {}", e.getClass().getSimpleName());
            return MatchReasonResult.unavailable();
        }
        if (body == null || body.get("reason") == null) {
            return MatchReasonResult.unavailable();
        }
        return MatchReasonResult.of(body.get("reason").toString());
    }
}
