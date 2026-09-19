package team4.emotionmap.memory.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import team4.emotionmap.contracts.ai.AnalysisRequest;
import team4.emotionmap.contracts.ai.AnalysisResult;
import team4.emotionmap.contracts.ai.ModerationRequest;
import team4.emotionmap.contracts.ai.ModerationVerdict;
import team4.emotionmap.contracts.ai.TieBreakRequest;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;
import team4.emotionmap.contracts.fixtures.DictionaryFixtures;
import team4.emotionmap.contracts.memory.AtmosphereAnalysisStatus;
import team4.emotionmap.contracts.memory.CategoryAnalysisStatus;

/** A09/A12/A31 의 mock 시나리오: 부분/전체 실패, 미분류, 안전 실패와 분류 실패의 구분. */
class MockAdaptersTest {

    private final AiProperties props = new AiProperties("mock", Duration.ofSeconds(5),
            "mock-analysis", "mock-v1", "mock-moderation", "mock-v1");
    private final MockAnalysisAdapter analysis = new MockAnalysisAdapter(props);
    private final MockModerationAdapter moderation = new MockModerationAdapter(props);

    @Test
    void happyPathClassifiesFromLabels() {
        AnalysisResult r = analysis.analyze(AnalysisRequest.of(DictionaryFixtures.SAMPLE_CONTENT, Duration.ofSeconds(5)));
        assertThat(r.atmosphereStatus()).isEqualTo(AtmosphereAnalysisStatus.SUCCEEDED);
        assertThat(r.atmospheres().toComplete()).contains(DictionaryFixtures.QUIET_COZY_TOGETHER_LONG);
        assertThat(r.categories()).containsExactly(PlaceCategoryCode.CAFE, PlaceCategoryCode.STUDY_WORK);
        assertThat(r.categoryStatus()).isEqualTo(CategoryAnalysisStatus.SUCCEEDED);
        assertThat(r.provenance().model()).isEqualTo("mock-analysis");
    }

    @Test
    void partialAndUnclassifiedNeverFillWithOther() {
        AnalysisResult partial = analysis.analyze(AnalysisRequest.of("[[AI_PARTIAL]] 조용하고 아늑한 곳에 함께 갔다", Duration.ofSeconds(5)));
        assertThat(partial.atmosphereStatus()).isEqualTo(AtmosphereAnalysisStatus.PARTIAL);
        assertThat(partial.atmospheres().stayStyle()).isNull();
        // 근거 없는 축은 추측하지 않고 null (§4.1)
        AnalysisResult noEvidence = analysis.analyze(AnalysisRequest.of("어딘가의 저녁", Duration.ofSeconds(5)));
        assertThat(noEvidence.atmospheres().isEmpty()).isTrue();
        assertThat(noEvidence.atmosphereStatus()).isEqualTo(AtmosphereAnalysisStatus.FAILED);
        assertThat(noEvidence.isUpstreamFailure()).isFalse();   // 카테고리는 INSUFFICIENT 이므로 상류 실패 아님
        assertThat(partial.categoryStatus()).isEqualTo(CategoryAnalysisStatus.INSUFFICIENT);
        assertThat(partial.categories()).doesNotContain(PlaceCategoryCode.OTHER).isEmpty();

        AnalysisResult unclassified = analysis.analyze(AnalysisRequest.of("[[AI_UNCLASSIFIED]] 카페", Duration.ofSeconds(5)));
        assertThat(unclassified.categories()).isEmpty();
    }

    @Test
    void upstreamFailureIsResultNotException() {
        AnalysisResult failed = analysis.analyze(AnalysisRequest.of("[[AI_FAIL]] 무엇이든", Duration.ofSeconds(5)));
        assertThat(failed.isUpstreamFailure()).isTrue();
        assertThat(failed.atmospheres().isEmpty()).isTrue();
        assertThat(failed.categories()).isEmpty();
    }

    @Test
    void moderationOutcomesAreSeparateFromClassification() {
        assertThat(moderation.moderate(new ModerationRequest("안전한 본문", null, Duration.ofSeconds(5))).verdict())
                .isEqualTo(ModerationVerdict.APPROVED);
        assertThat(moderation.moderate(new ModerationRequest("[[UNSAFE]] x", null, Duration.ofSeconds(5))).verdict())
                .isEqualTo(ModerationVerdict.REJECTED);
        assertThat(moderation.moderate(new ModerationRequest("[[MOD_ERROR]] x", null, Duration.ofSeconds(5))).verdict())
                .isEqualTo(ModerationVerdict.ERROR);
        // 분류가 성공해도 안전 실패면 배달 승인 아님 (§4.2)
        AnalysisResult ok = analysis.analyze(AnalysisRequest.of("[[UNSAFE]] 카페", Duration.ofSeconds(5)));
        assertThat(ok.categoryStatus()).isEqualTo(CategoryAnalysisStatus.SUCCEEDED);
        assertThat(ModerationVerdict.REJECTED.toStatus().allowsDelivery()).isFalse();
    }

    @Test
    void tieBreakReturnsPermutationOrFailure() {
        MockPreferenceTieBreakAdapter tie = new MockPreferenceTieBreakAdapter(props);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        var candidates = List.of(new TieBreakRequest.Candidate(a, "a"), new TieBreakRequest.Candidate(b, "b"));
        assertThat(tie.rank(new TieBreakRequest("조용한 곳", candidates, Duration.ofSeconds(5))).rankedMemoryIds())
                .containsExactly(a, b);
        assertThat(tie.rank(new TieBreakRequest("[[TIE_FAIL]]", candidates, Duration.ofSeconds(5))).failed()).isTrue();
    }
}
