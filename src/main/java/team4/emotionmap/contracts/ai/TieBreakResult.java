package team4.emotionmap.contracts.ai;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 동률 평가 결과. 성공이면 {@code rankedMemoryIds} 는 <b>요청에 있던 ID 들의 순열</b>이어야 하며,
 * 아니면 BE2 가 실패로 취급하고 무작위 처리한다(BE2 책임). 실패는 {@code failed=true}.
 */
public record TieBreakResult(boolean failed, List<UUID> rankedMemoryIds, AnalysisProvenance provenance, String failureReason) {

    public TieBreakResult {
        Objects.requireNonNull(provenance, "provenance");
        rankedMemoryIds = rankedMemoryIds == null ? List.of() : List.copyOf(rankedMemoryIds);
        if (failed && !rankedMemoryIds.isEmpty()) {
            throw new IllegalArgumentException("failed result carries no ranking");
        }
        if (!failed && rankedMemoryIds.isEmpty()) {
            throw new IllegalArgumentException("successful result needs a ranking");
        }
    }

    public static TieBreakResult failed(AnalysisProvenance provenance, String reason) {
        return new TieBreakResult(true, List.of(), provenance, reason);
    }

    public static TieBreakResult ranked(List<UUID> ranked, AnalysisProvenance provenance) {
        return new TieBreakResult(false, ranked, provenance, null);
    }
}
