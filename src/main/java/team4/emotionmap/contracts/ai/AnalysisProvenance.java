package team4.emotionmap.contracts.ai;

import java.util.Objects;

/**
 * 어떤 모델·프롬프트·사전 버전으로 분석했는지. {@code memories.analysis_model/analysis_prompt_version} 에 남는
 * 재현 메타데이터이며, 요청 로그 ID 같은 원문 추적 키는 넣지 않는다(ERD 6.3).
 */
public record AnalysisProvenance(String model, String promptVersion, int axisDefinitionVersion, int taxonomyVersion) {

    public AnalysisProvenance {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(promptVersion, "promptVersion");
    }
}
