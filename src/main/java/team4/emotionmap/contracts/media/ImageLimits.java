package team4.emotionmap.contracts.media;

import team4.emotionmap.contracts.config.ServiceLimits;

/** 이미지 검증 한도. 값은 {@code /config.limits} 에서 온다(하드코딩 금지). */
public record ImageLimits(long maxBytes, int maxWidth, int maxHeight, long maxPixels) {

    public ImageLimits {
        if (maxBytes < 1 || maxWidth < 1 || maxHeight < 1 || maxPixels < 1) {
            throw new IllegalArgumentException("image limits must be >= 1");
        }
    }

    public static ImageLimits from(ServiceLimits limits) {
        return new ImageLimits(limits.imageMaxBytes(), limits.imageMaxWidth(),
                limits.imageMaxHeight(), limits.imageMaxPixels());
    }
}
