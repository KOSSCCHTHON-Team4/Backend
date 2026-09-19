package team4.emotionmap.memory.ai;

import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import team4.emotionmap.contracts.ai.AnalysisProvenance;
import team4.emotionmap.contracts.ai.PreferenceTieBreakPort;
import team4.emotionmap.contracts.ai.TieBreakRequest;
import team4.emotionmap.contracts.ai.TieBreakResult;
import team4.emotionmap.contracts.dictionary.AtmosphereAxis;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;

/**
 * {@link PreferenceTieBreakPort} 가짜 구현(BE2 B01 소비). 취향 설명에 {@code [[TIE_FAIL]]} 이 있으면 실패,
 * 아니면 <b>요청 순서 그대로</b> 순위를 돌려준다(항상 유효한 순열). 무작위 폴백은 BE2 책임.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = AiProperties.MOCK, matchIfMissing = true)
public class MockPreferenceTieBreakAdapter implements PreferenceTieBreakPort {

    static final String FAIL_MARKER = "[[TIE_FAIL]]";

    private final AnalysisProvenance provenance;

    public MockPreferenceTieBreakAdapter(AiProperties properties) {
        this.provenance = new AnalysisProvenance(
                properties.analysisModel() == null ? "mock-analysis" : properties.analysisModel(),
                properties.analysisPromptVersion() == null ? "mock-v1" : properties.analysisPromptVersion(),
                AtmosphereAxis.DEFINITION_VERSION, PlaceCategoryCode.TAXONOMY_VERSION);
    }

    @Override
    public TieBreakResult rank(TieBreakRequest request) {
        if (request.preferenceDescription().contains(FAIL_MARKER)) {
            return TieBreakResult.failed(provenance, "MOCK_UPSTREAM_FAILURE");
        }
        List<UUID> ranked = request.candidates().stream().map(TieBreakRequest.Candidate::memoryId).toList();
        return TieBreakResult.ranked(ranked, provenance);
    }
}
