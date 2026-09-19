package team4.emotionmap.media;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "image_uploads")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ImageUpload {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "storage_path", unique = true, columnDefinition = "text")
    private String storagePath;

    @Column(name = "media_type", nullable = false, columnDefinition = "text")
    private String mediaType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(nullable = false)
    private Integer width;

    @Column(nullable = false)
    private Integer height;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private ImageUploadStatus status = ImageUploadStatus.STAGED;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "attached_memory_id", unique = true)
    private UUID attachedMemoryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public void attach(UUID memoryId) {
        if (memoryId == null || status != ImageUploadStatus.STAGED || storagePath == null) {
            throw new IllegalStateException("Only a staged upload with storage may be attached");
        }
        attachedMemoryId = memoryId;
        status = ImageUploadStatus.ATTACHED;
    }

    public boolean expireIfDue(Instant now) {
        Objects.requireNonNull(now, "now");
        if (status != ImageUploadStatus.STAGED || attachedMemoryId != null || expiresAt == null
                || expiresAt.isAfter(now)) {
            return false;
        }
        status = ImageUploadStatus.EXPIRED;
        storagePath = null;
        return true;
    }
}
