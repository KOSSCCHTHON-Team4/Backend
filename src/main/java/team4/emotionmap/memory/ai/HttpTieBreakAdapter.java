package team4.emotionmap.memory.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import team4.emotionmap.contracts.ai.AnalysisProvenance;
import team4.emotionmap.contracts.ai.PreferenceTieBreakPort;
import team4.emotionmap.contracts.ai.TieBreakRequest;
import team4.emotionmap.contracts.ai.TieBreakResult;
import team4.emotionmap.contracts.dictionary.AtmosphereAxis;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;

/**
 * {@link PreferenceTieBreakPort} 의 HTTP 구현(C09/B01). AI 서버 {@code POST /ai/tiebreak} 호출.
 *
 * <p><b>id 별칭 매핑:</b> AI(tiebreak)는 LLM 이 후보 id 를 그대로 복제해 점수에 실어 돌려줘야 하는데,
 * UUID 처럼 긴 문자열은 LLM 이 정확히 복제하지 못해 매칭이 깨진다(AI 서버가 "유효한 점수 없음" FAILED 반환).
 * 그래서 어댑터가 후보마다 짧은 별칭({@code c0,c1,...})을 부여해 AI 로 보내고, 응답 topIds(별칭)를 다시 UUID 로 되돌린다.
 *
 * <p><b>결과 계약:</b> 성공 시 {@link TieBreakResult#rankGroups} 는 요청 후보의 정확한 분할이어야 한다.
 * 1그룹=공동 1위(topIds→UUID), 2그룹=나머지 후보. SKIPPED/FAILED·무효·연결 실패는 모두 {@link TieBreakResult#failed}
 * → BE2 가 최고점 범위에서 무작위 처리.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = AiProperties.HTTP)
public class HttpTieBreakAdapter implements PreferenceTieBreakPort {

    private final RestClient client;
    private final AnalysisProvenance provenance;

    public HttpTieBreakAdapter(RestClient aiRestClient, AiProperties properties) {
        this.client = aiRestClient;
        this.provenance = new AnalysisProvenance("ai-tiebreak", "http-v1",
                AtmosphereAxis.DEFINITION_VERSION, PlaceCategoryCode.TAXONOMY_VERSION);
    }

    @Override
    @SuppressWarnings("unchecked")
    public TieBreakResult rank(TieBreakRequest request) {
        long start = System.nanoTime();

        // 후보에 짧은 별칭 부여(c0,c1,...) → AI 로 보냄. 원래 순서 보존.
        List<UUID> order = new ArrayList<>();
        Map<String, UUID> aliasToId = new LinkedHashMap<>();
        List<Map<String, Object>> candidates = new ArrayList<>();
        int i = 0;
        for (TieBreakRequest.Candidate c : request.candidates()) {
            if (order.contains(c.memoryId())) {
                continue; // 중복 방어
            }
            String alias = "c" + i++;
            aliasToId.put(alias, c.memoryId());
            order.add(c.memoryId());
            candidates.add(Map.of("id", alias, "content", c.content()));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("preferenceText", request.preferenceDescription());
        payload.put("candidates", candidates);

        Map<String, Object> body;
        try {
            body = client.post().uri("/ai/tiebreak")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload).retrieve().body(Map.class);
        } catch (RuntimeException e) {
            log.warn("ai tiebreak call failed: {}", e.getClass().getSimpleName());
            return TieBreakResult.failed(provenance, "AI_TIEBREAK_CALL_FAILED");
        }
        if (body == null || !"SUCCEEDED".equals(str(body.get("status")))) {
            log.info("ai tiebreak non-success durMs={} status={}", durMs(start),
                    body == null ? "null" : str(body.get("status")));
            return TieBreakResult.failed(provenance, "AI_TIEBREAK_NOT_SUCCEEDED");
        }

        // topIds(별칭) → UUID 역매핑. 알 수 없는 별칭은 버린다.
        List<UUID> top = new ArrayList<>();
        Object raw = body.get("topIds");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                UUID id = aliasToId.get(str(o));
                if (id != null && !top.contains(id)) {
                    top.add(id);
                }
            }
        }
        if (top.isEmpty()) {
            return TieBreakResult.failed(provenance, "AI_TIEBREAK_INVALID_TOP_IDS");
        }

        // rankGroups: 요청 후보의 정확한 분할. 1그룹=공동 1위, 2그룹=나머지.
        List<UUID> rest = new ArrayList<>();
        for (UUID id : order) {
            if (!top.contains(id)) {
                rest.add(id);
            }
        }
        List<List<UUID>> rankGroups = new ArrayList<>();
        rankGroups.add(top);
        if (!rest.isEmpty()) {
            rankGroups.add(rest);
        }
        log.info("ai tiebreak ok durMs={} topCount={} groups={}", durMs(start), top.size(), rankGroups.size());
        return TieBreakResult.ranked(rankGroups, provenance);
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static long durMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
