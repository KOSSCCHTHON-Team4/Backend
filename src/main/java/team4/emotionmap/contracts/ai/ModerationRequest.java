package team4.emotionmap.contracts.ai;

import java.time.Duration;
import java.util.Objects;
import team4.emotionmap.contracts.media.SanitizedImage;

/** 안전 검사 입력: 본문과 선택 이미지(재인코딩된 것). 이미지 없으면 null. */
public record ModerationRequest(String content, SanitizedImage image, Duration timeout) {

    public ModerationRequest {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(timeout, "timeout");
        if (content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    public boolean hasImage() {
        return image != null;
    }
}
