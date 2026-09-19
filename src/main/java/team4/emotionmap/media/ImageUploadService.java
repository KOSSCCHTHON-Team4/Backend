package team4.emotionmap.media;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import team4.emotionmap.account.AccountAccessService;
import team4.emotionmap.account.User;
import team4.emotionmap.account.UserRepository;
import team4.emotionmap.media.dto.ImageUploadResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageUploadService {
    private final ImageStorageService imageStorageService;
    private final ImageUploadRepository imageUploadRepository;
    private final UserRepository userRepository;

    @Value("${app.storage.staging-ttl:PT30M}")
    private Duration stagingTtl;

    @Transactional
    public ImageUploadResponse upload(UUID ownerId, MultipartFile file) {
        User owner = userRepository.findByIdForUpdate(ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
        AccountAccessService.requireActive(owner);
        if (stagingTtl.isZero() || stagingTtl.isNegative()) {
            throw new IllegalStateException("Image staging TTL must be positive");
        }
        ImageStorageService.StoredUpload stored = imageStorageService.store(file);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        imageStorageService.delete(stored.key());
                    } catch (RuntimeException cleanup) {
                        log.error("Failed to clean up rolled-back image upload {}", stored.key(), cleanup);
                    }
                }
            }
        });
        Instant now = Instant.now();
        ImageUpload upload = imageUploadRepository.save(ImageUpload.builder()
                .ownerId(ownerId).storagePath(stored.key()).mediaType(stored.mediaType())
                .sizeBytes(stored.sizeBytes()).width(stored.width()).height(stored.height())
                .createdAt(now).expiresAt(now.plus(stagingTtl)).build());
        return ImageUploadResponse.from(upload);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ImageUpload requireAttachable(UUID ownerId, UUID imageId) {
        ImageUpload upload = imageUploadRepository.findByIdForUpdate(imageId)
                .filter(image -> image.getOwnerId().equals(ownerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image upload not found"));
        if (upload.getStatus() == ImageUploadStatus.ATTACHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "IMAGE_ALREADY_ATTACHED");
        }
        if (upload.getStatus() != ImageUploadStatus.STAGED || !upload.getExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Image upload expired");
        }
        imageStorageService.load(upload.getStoragePath());
        return upload;
    }
}
