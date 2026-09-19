package team4.emotionmap.contracts.media;

import java.util.Objects;

/**
 * 로컬 저장소에 기록된 파일의 메타데이터. {@code storageKey} 는 서버가 만든 무작위 상대 키
 * (사용자 ID·경험 ID·원본 파일명 포함 금지). 절대 경로는 밖으로 나가지 않는다.
 */
public record StoredImageMeta(String storageKey, ImageMediaType mediaType, long sizeBytes, int width, int height) {

    public StoredImageMeta {
        Objects.requireNonNull(storageKey, "storageKey");
        Objects.requireNonNull(mediaType, "mediaType");
        if (storageKey.isBlank() || sizeBytes < 1 || width < 1 || height < 1) {
            throw new IllegalArgumentException("invalid stored image meta");
        }
    }
}
