package team4.emotionmap.contracts.media;

import java.util.Optional;

/** 허용 이미지 형식은 두 가지뿐(API_SPEC 8.10). 확장자·클라이언트 MIME 이 아니라 실제 바이트로 판정한다. */
public enum ImageMediaType {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png");

    private final String mimeType;
    private final String extension;

    ImageMediaType(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }

    public String mimeType() {
        return mimeType;
    }

    public String extension() {
        return extension;
    }

    public static Optional<ImageMediaType> fromMimeType(String mimeType) {
        for (ImageMediaType t : values()) {
            if (t.mimeType.equals(mimeType)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
