package team4.emotionmap.contracts.memory;

import java.util.Objects;
import team4.emotionmap.contracts.media.ImageMediaType;

/**
 * 경험에 붙은 사진 메타({@code image_path/image_media_type/image_size_bytes}).
 * {@code storageKey} 는 서버가 만든 무작위 상대 키이며 원문 ID·사용자 ID 를 포함하지 않는다.
 */
public record ImageAttachment(String storageKey, ImageMediaType mediaType, long sizeBytes) {

    public ImageAttachment {
        Objects.requireNonNull(storageKey, "storageKey");
        Objects.requireNonNull(mediaType, "mediaType");
        if (storageKey.isBlank() || sizeBytes < 1) {
            throw new IllegalArgumentException("invalid image attachment");
        }
    }
}
