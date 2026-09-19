package team4.emotionmap.contracts.ai;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * 2차 판정 입력(기획 §7·§8 {@code /ai/verify}): 추천받는 사용자의 취향 문장 + 해당 장소 리뷰 상위 3~5개.
 * 작성자·좌표·사용자 식별자는 보내지 않는다.
 */
public record VerifyRequest(String preferenceText, List<String> reviews, Duration timeout) {

    public VerifyRequest {
        Objects.requireNonNull(preferenceText, "preferenceText");
        Objects.requireNonNull(timeout, "timeout");
        reviews = List.copyOf(Objects.requireNonNull(reviews, "reviews"));
        if (preferenceText.isBlank()) {
            throw new IllegalArgumentException("preferenceText must not be blank");
        }
        if (reviews.isEmpty()) {
            throw new IllegalArgumentException("at least one review is required");
        }
    }
}
