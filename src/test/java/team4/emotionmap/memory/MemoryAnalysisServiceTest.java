package team4.emotionmap.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import team4.emotionmap.contracts.ai.AnalysisEnrichment;
import team4.emotionmap.contracts.ai.AnalysisPort;
import team4.emotionmap.contracts.ai.AnalysisResult;
import team4.emotionmap.contracts.config.AuthConfig;
import team4.emotionmap.contracts.config.MatchingProperties;
import team4.emotionmap.contracts.config.ServiceConfig;
import team4.emotionmap.contracts.config.ServiceConfigSource;
import team4.emotionmap.contracts.config.ServiceLimits;
import team4.emotionmap.contracts.dictionary.AnalyzedAtmospheres;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;
import team4.emotionmap.contracts.fixtures.DictionaryFixtures;
import team4.emotionmap.contracts.geo.GeoPoint;
import team4.emotionmap.memory.ai.AiProperties;
import team4.emotionmap.memory.analysis.AnalysisReceipt;
import team4.emotionmap.memory.analysis.AnalysisReceiptCodec;
import team4.emotionmap.memory.dto.AnalyzeRequest;
import team4.emotionmap.memory.dto.AnalyzeResponse;
import team4.emotionmap.platform.security.HmacSignedValueCodecTestSupport;

/** A04 + 기획 §5·§8: 네이버 매핑 우선, 저신뢰도 → OTHER 제안, 마스킹 본문 토큰 인정, 실패 → 200+FAILED. */
class MemoryAnalysisServiceTest {

    private final AnalysisPort port = mock(AnalysisPort.class);
    private final AnalysisReceiptCodec codec = new AnalysisReceiptCodec(HmacSignedValueCodecTestSupport.codec());
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-20T03:00:00Z"), ZoneOffset.UTC);
    private final ServiceConfigSource config = () -> new ServiceConfig("t", 1000, new GeoPoint(37.6, 127.0),
            new ServiceLimits(3000, 1000, 1000, 5_000_000L, 6000, 6000, 20_000_000L, 1800, 900, 20, 20, 50, 200),
            new AuthConfig(3600, 5, 60));
    private final MemoryAnalysisService service = new MemoryAnalysisService(port, codec, config,
            MatchingProperties.defaults(),
            new AiProperties("mock", Duration.ofSeconds(5), "m", "v", "mm", "mv", null, null), clock);
    private final UUID user = DictionaryFixtures.userId(1);

    @Test
    void successfulAnalysisReturnsTokenAndExtras() {
        AnalysisEnrichment e = new AnalysisEnrichment(Map.of("조용한", "사람이 거의 없어서"), List.of("창가", "독서"),
                0.95, "ai", null, false, true, null);
        when(port.analyze(any())).thenReturn(AnalysisResult.of(DictionaryFixtures.PARTIAL_STAY_UNKNOWN,
                DictionaryFixtures.CAFE_STUDY, DictionaryFixtures.MOCK_PROVENANCE, e));

        AnalyzeResponse r = service.analyze(user, new AnalyzeRequest(DictionaryFixtures.SAMPLE_CONTENT, null));

        assertThat(r.categoryStatus()).isEqualTo("CLASSIFIED");
        assertThat(r.atmosphereStatus()).isEqualTo("PARTIAL");
        assertThat(r.categories()).containsExactly("CAFE", "STUDY_WORK");
        assertThat(r.warnings()).containsExactly(AnalyzeResponse.WARN_ATMOSPHERE_NEEDS_INPUT);
        assertThat(r.evidence()).containsEntry("조용한", "사람이 거의 없어서");
        assertThat(r.tags()).containsExactly("창가", "독서");
        assertThat(r.expiresAt()).isEqualTo(clock.instant().plusSeconds(900));
        AnalysisReceipt receipt = codec.verify(r.analysisToken(), user, DictionaryFixtures.SAMPLE_CONTENT, clock.instant());
        assertThat(receipt.enrichment().tags()).containsExactly("창가", "독서");
        assertThat(receipt.enrichment().categoryConfidence()).isEqualTo(0.95);
        assertThat(receipt.provenance().axisDefinitionVersion()).isEqualTo(2);
    }

    @Test
    void naverCategoryMappingWinsOverAi() {
        when(port.analyze(any())).thenReturn(AnalysisResult.of(new AnalyzedAtmospheres(-1.0, -1.0, -1.0, -1.0),
                List.of(PlaceCategoryCode.STUDY_WORK), DictionaryFixtures.MOCK_PROVENANCE));
        AnalyzeResponse r = service.analyze(user, new AnalyzeRequest("조용한 곳", "카페,디저트>카페"));
        assertThat(r.categories()).containsExactly("CAFE", "STUDY_WORK");
        assertThat(r.categorySource()).isEqualTo("naver");
        assertThat(r.categoryConfidence()).isEqualTo(1.0);
    }

    @Test
    void lowConfidenceBecomesOtherSuggestionWithWarning() {
        AnalysisEnrichment low = new AnalysisEnrichment(Map.of(), List.of(), 0.4, "ai", null, false, true, null);
        when(port.analyze(any())).thenReturn(AnalysisResult.of(new AnalyzedAtmospheres(-1.0, -1.0, -1.0, -1.0),
                List.of(PlaceCategoryCode.SHOPPING), DictionaryFixtures.MOCK_PROVENANCE, low));
        AnalyzeResponse r = service.analyze(user, new AnalyzeRequest("애매한 어딘가", null));
        assertThat(r.categories()).containsExactly("OTHER");
        assertThat(r.warnings()).contains(AnalyzeResponse.WARN_CATEGORY_LOW_CONFIDENCE);
    }

    @Test
    void upstreamFailureIsTwoHundredFailedAndNeverOther() {
        when(port.analyze(any())).thenReturn(AnalysisResult.failed(DictionaryFixtures.MOCK_PROVENANCE, "TIMEOUT"));
        AnalyzeResponse r = service.analyze(user, new AnalyzeRequest("아무 본문", null));
        assertThat(r.categoryStatus()).isEqualTo("FAILED");
        assertThat(r.atmosphereStatus()).isEqualTo("FAILED");
        assertThat(r.categories()).isEmpty();
        assertThat(r.atmospheres().isEmpty()).isTrue();
        assertThat(r.warnings()).containsExactly(AnalyzeResponse.WARN_ANALYSIS_FAILED);
        assertThat(r.analysisToken()).isNotBlank();
    }

    @Test
    void maskedContentIsAcceptedBySameToken() {
        String original = "여기 연락처 010-1234-5678 로 예약하세요. 조용한 카페.";
        String masked = "여기 연락처 [전화번호] 로 예약하세요. 조용한 카페.";
        AnalysisEnrichment e = new AnalysisEnrichment(Map.of(), List.of(), 0.9, "ai", masked, true, true, null);
        when(port.analyze(any())).thenReturn(AnalysisResult.of(new AnalyzedAtmospheres(-1.0, -1.0, -1.0, -1.0),
                List.of(PlaceCategoryCode.CAFE), DictionaryFixtures.MOCK_PROVENANCE, e));
        AnalyzeResponse r = service.analyze(user, new AnalyzeRequest(original, null));
        assertThat(r.maskedContent()).isEqualTo(masked);
        assertThat(r.piiFound()).isTrue();
        assertThat(r.warnings()).contains(AnalyzeResponse.WARN_PII_MASKED);
        assertThat(codec.verify(r.analysisToken(), user, original, clock.instant()).isMaskedSubmission(original)).isFalse();
        assertThat(codec.verify(r.analysisToken(), user, masked, clock.instant()).isMaskedSubmission(masked)).isTrue();
    }
}
