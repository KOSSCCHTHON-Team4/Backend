package team4.emotionmap.contracts.ai;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 최고점 동률 후보의 자연어 취향 평가 입력(B01). 후보는 <b>ID + 본문</b>만 넘기고 작성자 정보는 넣지 않는다.
 *
 * @param preferenceDescription 수신자의 자연어 취향(null 이면 호출하지 않는 것이 원칙)
 */
public record TieBreakRequest(String preferenceDescription, List<Candidate> candidates, Duration timeout) {

    public record Candidate(UUID memoryId, String content) {
        public Candidate {
            Objects.requireNonNull(memoryId);
            Objects.requireNonNull(content);
        }
    }

    public TieBreakRequest {
        Objects.requireNonNull(preferenceDescription, "preferenceDescription");
        Objects.requireNonNull(timeout, "timeout");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        if (candidates.size() < 2) {
            throw new IllegalArgumentException("tie-break needs at least two candidates");
        }
        if (candidates.stream().map(Candidate::memoryId).distinct().count() != candidates.size()) {
            throw new IllegalArgumentException("tie-break candidates must have distinct memory IDs");
        }
    }
}
