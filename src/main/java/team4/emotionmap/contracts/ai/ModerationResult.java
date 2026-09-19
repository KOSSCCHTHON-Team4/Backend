package team4.emotionmap.contracts.ai;

import java.util.List;
import java.util.Objects;

/** 안전 검사 결과. {@code reasonCodes} 는 짧은 코드 목록(본문 인용 금지). APPROVED 면 비어 있다. */
public record ModerationResult(ModerationVerdict verdict, List<String> reasonCodes, AnalysisProvenance provenance) {

    public ModerationResult {
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(provenance, "provenance");
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        if (verdict == ModerationVerdict.APPROVED && !reasonCodes.isEmpty()) {
            throw new IllegalArgumentException("APPROVED carries no reason codes");
        }
    }

    public static ModerationResult approved(AnalysisProvenance provenance) {
        return new ModerationResult(ModerationVerdict.APPROVED, List.of(), provenance);
    }

    public static ModerationResult error(AnalysisProvenance provenance, String reasonCode) {
        return new ModerationResult(ModerationVerdict.ERROR, List.of(reasonCode), provenance);
    }
}
