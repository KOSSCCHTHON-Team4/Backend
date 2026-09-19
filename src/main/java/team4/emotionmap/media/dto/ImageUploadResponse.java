package team4.emotionmap.media.dto;

import java.time.Instant;
import java.util.UUID;
import team4.emotionmap.media.ImageUpload;

public record ImageUploadResponse(
        UUID imageId, Instant expiresAt, String mediaType, long sizeBytes, int width, int height
) {
    public static ImageUploadResponse from(ImageUpload upload) {
        return new ImageUploadResponse(upload.getId(), upload.getExpiresAt(), upload.getMediaType(),
                upload.getSizeBytes(), upload.getWidth(), upload.getHeight());
    }
}
