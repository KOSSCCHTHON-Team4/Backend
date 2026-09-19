package team4.emotionmap.memory.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import team4.emotionmap.contracts.ai.AnalysisRequest;
import team4.emotionmap.contracts.ai.AnalysisResult;
import team4.emotionmap.contracts.ai.ModerationRequest;
import team4.emotionmap.contracts.ai.ModerationVerdict;
import team4.emotionmap.contracts.ai.TieBreakRequest;
import team4.emotionmap.contracts.ai.TieBreakResult;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;
import team4.emotionmap.contracts.memory.AtmosphereAnalysisStatus;
import team4.emotionmap.contracts.memory.CategoryAnalysisStatus;

/**
 * HTTP 어댑터 변환·실패 계약 단위 테스트 (DB·실제 AI 서버 불필요).
 * MockRestServiceServer 로 AI 서버 응답을 흉내 내어 어댑터가 계약대로 매핑하는지 검증한다.
 */
class HttpAdaptersTest {

    private final AiProperties props = new AiProperties("http", Duration.ofSeconds(5),
            "ai-analyze", "http-v1", "ai-moderate", "http-v1", "http://localhost:8000", null);

    private RestClient.Builder builder() {
        return RestClient.builder().baseUrl("http://localhost:8000");
    }

    // ---------------------------------------------------------------- analyze
    @Test
    void analyzeMapsAllAxesAndCategories() {
        RestClient.Builder b = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("http://localhost:8000/ai/analyze")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"atmospheres":{"CROWD_LEVEL":-1,"SPATIAL_FEEL":-1,"COMPANY_FIT":-1,"STAY_STYLE":-1},
                         "categories":["CAFE"],"categoryStatus":"CLASSIFIED","atmosphereStatus":"SUCCEEDED",
                         "model":"claude-haiku-4-5","promptVersion":"analyze-v1","dictionaryVersion":1,"error":null}
                        """, MediaType.APPLICATION_JSON));
        HttpAnalysisAdapter adapter = new HttpAnalysisAdapter(b.build(), props);

        AnalysisResult r = adapter.analyze(AnalysisRequest.of("조용하고 아늑한 혼자 오래 카페", Duration.ofSeconds(5)));

        assertThat(r.atmosphereStatus()).isEqualTo(AtmosphereAnalysisStatus.SUCCEEDED);
        assertThat(r.categories()).containsExactly(PlaceCategoryCode.CAFE);
        assertThat(r.provenance().model()).isEqualTo("claude-haiku-4-5");
        server.verify();
    }

    @Test
    void analyzePartialWhenSomeAxesNull() {
        RestClient.Builder b = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("http://localhost:8000/ai/analyze"))
                .andRespond(withSuccess("""
                        {"atmospheres":{"CROWD_LEVEL":-1,"SPATIAL_FEEL":null,"COMPANY_FIT":-1,"STAY_STYLE":null},
                         "categories":[],"categoryStatus":"UNCLASSIFIED","atmosphereStatus":"PARTIAL",
                         "model":"m","promptVersion":"p","dictionaryVersion":1}
                        """, MediaType.APPLICATION_JSON));
        HttpAnalysisAdapter adapter = new HttpAnalysisAdapter(b.build(), props);

        AnalysisResult r = adapter.analyze(AnalysisRequest.of("조용 혼자", Duration.ofSeconds(5)));

        assertThat(r.atmosphereStatus()).isEqualTo(AtmosphereAnalysisStatus.PARTIAL);
        assertThat(r.categories()).isEmpty();
        assertThat(r.categoryStatus()).isEqualTo(CategoryAnalysisStatus.INSUFFICIENT);
    }

    @Test
    void analyzeReturnsFailedOnServerError() {
        RestClient.Builder b = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("http://localhost:8000/ai/analyze")).andRespond(withServerError());
        HttpAnalysisAdapter adapter = new HttpAnalysisAdapter(b.build(), props);

        AnalysisResult r = adapter.analyze(AnalysisRequest.of("x", Duration.ofSeconds(5)));

        assertThat(r.isUpstreamFailure()).isTrue();
        assertThat(r.failureReason()).isNotBlank();
    }

    // ---------------------------------------------------------------- moderate
    @Test
    void moderateMapsDecision() {
        RestClient.Builder b = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("http://localhost:8000/ai/moderate"))
                .andRespond(withSuccess("{\"decision\":\"APPROVED\",\"flagged\":false}", MediaType.APPLICATION_JSON));
        HttpModerationAdapter adapter = new HttpModerationAdapter(b.build(), props);

        var r = adapter.moderate(new ModerationRequest("정상 본문", null, Duration.ofSeconds(5)));

        assertThat(r.verdict()).isEqualTo(ModerationVerdict.APPROVED);
        assertThat(r.reasonCodes()).isEmpty();
    }

    @Test
    void moderateErrorOnServerFailure() {
        RestClient.Builder b = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("http://localhost:8000/ai/moderate")).andRespond(withServerError());
        HttpModerationAdapter adapter = new HttpModerationAdapter(b.build(), props);

        var r = adapter.moderate(new ModerationRequest("본문", null, Duration.ofSeconds(5)));

        assertThat(r.verdict()).isEqualTo(ModerationVerdict.ERROR);
    }

    // ---------------------------------------------------------------- tiebreak
    @Test
    void tiebreakMapsTopIdsToRankGroups() {
        UUID a = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        RestClient.Builder b = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("http://localhost:8000/ai/tiebreak"))
                // 어댑터는 후보에 별칭 c0,c1... 을 부여해 보낸다. 요청 본문에 별칭이 실렸는지 확인.
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .jsonPath("$.candidates[1].id").value("c1"))
                .andRespond(withSuccess(
                        "{\"status\":\"SUCCEEDED\",\"topIds\":[\"c1\"],\"scores\":{}}",
                        MediaType.APPLICATION_JSON));
        HttpTieBreakAdapter adapter = new HttpTieBreakAdapter(b.build(), props);

        TieBreakRequest req = new TieBreakRequest("조용한 곳",
                List.of(new TieBreakRequest.Candidate(a, "북적"), new TieBreakRequest.Candidate(bId, "조용")),
                Duration.ofSeconds(5));
        TieBreakResult r = adapter.rank(req);

        // 별칭 c1 → bId 로 역매핑되고, rankGroups 가 요청 후보의 정확한 분할이어야 validateFor 통과.
        TieBreakResult.Validation v = r.validateFor(req);
        assertThat(v).isInstanceOf(TieBreakResult.Valid.class);
        assertThat(((TieBreakResult.Valid) v).topRankCandidates()).containsExactly(bId);
    }

    @Test
    void tiebreakFailedOnNonSuccessStatus() {
        UUID a = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        RestClient.Builder b = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("http://localhost:8000/ai/tiebreak"))
                .andRespond(withSuccess("{\"status\":\"SKIPPED\",\"topIds\":[]}", MediaType.APPLICATION_JSON));
        HttpTieBreakAdapter adapter = new HttpTieBreakAdapter(b.build(), props);

        TieBreakRequest req = new TieBreakRequest("설명",
                List.of(new TieBreakRequest.Candidate(a, "x"), new TieBreakRequest.Candidate(bId, "y")),
                Duration.ofSeconds(5));
        TieBreakResult r = adapter.rank(req);

        assertThat(r.failed()).isTrue();
        assertThat(r.validateFor(req)).isInstanceOf(TieBreakResult.Failure.class);
    }

    // ---------------------------------------------------------------- 기획 §8 부가 출력 / verify / match-reason
    @Test
    void analyzeParsesPlanExtrasAndSendsNaverCategory() {
        RestClient.Builder b = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("http://localhost:8000/ai/analyze")).andExpect(method(HttpMethod.POST))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.naver_category").value("카페,디저트>카페"))
                .andRespond(withSuccess("""
                        {"atmospheres":{"CROWD_LEVEL":-1,"SPATIAL_FEEL":-1,"COMPANY_FIT":-1,"STAY_STYLE":-1},
                         "categories":["CAFE"],"categoryStatus":"CLASSIFIED","atmosphereStatus":"SUCCEEDED",
                         "evidence":{"조용한":"사람이 거의 없어서"},"tags":["창가","독서"],
                         "category_confidence":0.95,"category_source":"naver",
                         "masked_content":"연락처 [전화번호] 조용한 카페","pii_found":true,"safe":true,"unsafe_reason":null,
                         "model":"m","promptVersion":"p"}
                        """, MediaType.APPLICATION_JSON));
        HttpAnalysisAdapter adapter = new HttpAnalysisAdapter(b.build(), props);

        AnalysisResult r = adapter.analyze(AnalysisRequest.of("연락처 010-1234-5678 조용한 카페", "카페,디저트>카페", Duration.ofSeconds(5)));

        assertThat(r.enrichment().evidence()).containsEntry("조용한", "사람이 거의 없어서");
        assertThat(r.enrichment().tags()).containsExactly("창가", "독서");
        assertThat(r.enrichment().categoryConfidence()).isEqualTo(0.95);
        assertThat(r.enrichment().categorySource()).isEqualTo("naver");
        assertThat(r.enrichment().piiFound()).isTrue();
        assertThat(r.enrichment().safe()).isTrue();
        assertThat(r.enrichment().hasMaskedContent("연락처 010-1234-5678 조용한 카페")).isTrue();
        server.verify();
    }

    @Test
    void verifyMapsFitAndFailsSafe() {
        RestClient.Builder b = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("http://localhost:8000/ai/verify")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"fit":true,"confidence":0.8,"reason":"혼자 책 읽기 좋다는 리뷰가 있어요.","evidence":["혼자 책"]}
                        """, MediaType.APPLICATION_JSON));
        HttpPreferenceVerifyAdapter adapter = new HttpPreferenceVerifyAdapter(b.build());
        var ok = adapter.verify(new team4.emotionmap.contracts.ai.VerifyRequest("비 오는 날 혼자 책 읽기 좋은 곳", List.of("리뷰1"), Duration.ofSeconds(5)));
        assertThat(ok.failed()).isFalse();
        assertThat(ok.fit()).isTrue();
        assertThat(ok.confidence()).isEqualTo(0.8);
        assertThat(ok.evidence()).containsExactly("혼자 책");

        RestClient.Builder b2 = builder();
        MockRestServiceServer down = MockRestServiceServer.bindTo(b2).build();
        down.expect(requestTo("http://localhost:8000/ai/verify")).andRespond(withServerError());
        var failed = new HttpPreferenceVerifyAdapter(b2.build())
                .verify(new team4.emotionmap.contracts.ai.VerifyRequest("취향", List.of("리뷰"), Duration.ofSeconds(5)));
        assertThat(failed.failed()).isTrue();
        assertThat(failed.fit()).isFalse();
    }

    @Test
    void matchReasonReturnsTextOrUnavailable() {
        RestClient.Builder b = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("http://localhost:8000/ai/match-reason")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"reason\":\"조용히 혼자 머물 곳을 찾는 당신에게 딱이에요.\"}", MediaType.APPLICATION_JSON));
        var ok = new HttpMatchReasonAdapter(b.build()).explain(new team4.emotionmap.contracts.ai.MatchReasonRequest(
                List.of("조용한", "혼자 가기 좋은"), "골목 카페", List.of("리뷰"), Duration.ofSeconds(5)));
        assertThat(ok.failed()).isFalse();
        assertThat(ok.reason()).startsWith("조용히");

        RestClient.Builder b2 = builder();
        MockRestServiceServer down = MockRestServiceServer.bindTo(b2).build();
        down.expect(requestTo("http://localhost:8000/ai/match-reason")).andRespond(withServerError());
        assertThat(new HttpMatchReasonAdapter(b2.build()).explain(new team4.emotionmap.contracts.ai.MatchReasonRequest(
                List.of("조용한"), null, List.of(), Duration.ofSeconds(5))).failed()).isTrue();
    }
}
