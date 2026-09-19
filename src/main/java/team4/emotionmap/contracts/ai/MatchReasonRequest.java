package team4.emotionmap.contracts.ai;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** 알림 문구 생성 입력(기획 §8 {@code /ai/match-reason}): 선택 카드 라벨·장소 표시명·리뷰 일부. */
public record MatchReasonRequest(List<String> cards, String placeName, List<String> reviews, Duration timeout) {

    public MatchReasonRequest {
        Objects.requireNonNull(timeout, "timeout");
        cards = List.copyOf(Objects.requireNonNull(cards, "cards"));
        reviews = List.copyOf(Objects.requireNonNull(reviews, "reviews"));
        if (cards.isEmpty()) {
            throw new IllegalArgumentException("at least one card is required");
        }
    }
}
