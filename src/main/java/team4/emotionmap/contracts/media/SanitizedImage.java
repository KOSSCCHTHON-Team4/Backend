package team4.emotionmap.contracts.media;

import java.util.Objects;

/**
 * 실제 디코딩·검사·재인코딩(EXIF 등 메타데이터 제거)을 통과한 이미지. 저장은 항상 이 타입으로만 한다.
 * {@code bytes} 는 원본이 아니라 <b>재인코딩 결과</b>다. Idempotency 지문은 원본 요청 바이트 기준이며 이 값이 아니다.
 */
public record SanitizedImage(byte[] bytes, ImageMediaType mediaType, int width, int height) {

    public SanitizedImage {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(mediaType, "mediaType");
        if (bytes.length == 0 || width < 1 || height < 1) {
            throw new IllegalArgumentException("invalid sanitized image");
        }
    }

    public long sizeBytes() {
        return bytes.length;
    }
}
