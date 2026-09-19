package team4.emotionmap.memory.ai;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import team4.emotionmap.contracts.ai.AnalysisProvenance;
import team4.emotionmap.contracts.ai.ModerationPort;
import team4.emotionmap.contracts.ai.ModerationRequest;
import team4.emotionmap.contracts.ai.ModerationResult;
import team4.emotionmap.contracts.ai.ModerationVerdict;
import team4.emotionmap.contracts.dictionary.AtmosphereAxis;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;

/**
 * {@link ModerationPort} 가짜 구현(C09/A07). 마커: {@code [[UNSAFE]]} → REJECTED,
 * {@code [[REVIEW]]} → REVIEW_REQUIRED, {@code [[MOD_ERROR]]} → ERROR(재시도 대상). 그 외 APPROVED.
 * 안전 실패는 분류 실패와 다른 결과 타입으로 나간다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = AiProperties.MOCK, matchIfMissing = true)
public class MockModerationAdapter implements ModerationPort {

    static final String UNSAFE_MARKER = "[[UNSAFE]]";
    static final String REVIEW_MARKER = "[[REVIEW]]";
    static final String ERROR_MARKER = "[[MOD_ERROR]]";

    private final AnalysisProvenance provenance;

    public MockModerationAdapter(AiProperties properties) {
        this.provenance = new AnalysisProvenance(
                properties.moderationModel() == null ? "mock-moderation" : properties.moderationModel(),
                properties.moderationPromptVersion() == null ? "mock-v1" : properties.moderationPromptVersion(),
                AtmosphereAxis.DEFINITION_VERSION, PlaceCategoryCode.TAXONOMY_VERSION);
    }

    @Override
    public ModerationResult moderate(ModerationRequest request) {
        String content = request.content();
        log.debug("mock moderation: contentLength={} hasImage={}", content.length(), request.hasImage());
        if (content.contains(ERROR_MARKER)) {
            return ModerationResult.error(provenance, "MOCK_UPSTREAM_ERROR");
        }
        if (content.contains(UNSAFE_MARKER)) {
            return new ModerationResult(ModerationVerdict.REJECTED, List.of("MOCK_UNSAFE"), provenance);
        }
        if (content.contains(REVIEW_MARKER)) {
            return new ModerationResult(ModerationVerdict.REVIEW_REQUIRED, List.of("MOCK_REVIEW"), provenance);
        }
        return ModerationResult.approved(provenance);
    }
}
