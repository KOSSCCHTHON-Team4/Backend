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
 * 아니면 요청 후보 전부를 하나의 공동 1위 그룹으로 돌려준다. 무작위 최종 선택은 BE2 책임이다.
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
        List<UUID> jointFirstPlace = request.candidates().stream()
                .map(TieBreakRequest.Candidate::memoryId)
                .toList();
        return TieBreakResult.ranked(List.of(jointFirstPlace), provenance);
    }
}
