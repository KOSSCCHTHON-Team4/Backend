package team4.emotionmap.media;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import team4.emotionmap.contracts.config.ServiceConfigSource;
import team4.emotionmap.contracts.config.ServiceLimits;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.media.ImageLimits;
import team4.emotionmap.contracts.media.SanitizedImage;
import team4.emotionmap.contracts.media.StoredImageMeta;
import team4.emotionmap.media.dto.ImageUploadResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageUploadService {
    private final ImageStorageService imageStorageService;
    private final ImageUploadRepository imageUploadRepository;
    private final UserRepository userRepository;
    private final ImageSanitizer imageSanitizer;
    private final ServiceConfigSource serviceConfigSource;

    @Transactional
    public ImageUploadResponse upload(UUID ownerId, MultipartFile file) {
        User owner = userRepository.findByIdForUpdate(ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
        AccountAccessService.requireActive(owner);

        ServiceLimits serviceLimits = serviceConfigSource.current().limits();
        ImageLimits imageLimits = ImageLimits.from(serviceLimits);
        byte[] original = readBounded(file, imageLimits.maxBytes());
        SanitizedImage sanitized = imageSanitizer.sanitize(original, imageLimits);
        StoredImageMeta stored = imageStorageService.store(sanitized);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        imageStorageService.delete(stored.storageKey());
                    } catch (RuntimeException ignored) {
                        log.error("Failed to clean up a rolled-back image upload");
                    }
                }
            }
        });

        Instant now = Instant.now();
        ImageUpload upload = imageUploadRepository.save(ImageUpload.builder()
                .ownerId(ownerId).storagePath(stored.storageKey()).mediaType(stored.mediaType().mimeType())
                .sizeBytes(stored.sizeBytes()).width(stored.width()).height(stored.height())
                .createdAt(now).expiresAt(now.plusSeconds(serviceLimits.imageUploadTtlSeconds())).build());
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

    private static byte[] readBounded(MultipartFile file, long maxBytes) {
        if (file == null) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST);
        }
        int maxReadBytes = maximumReadBytes(maxBytes);
        if (file.getSize() > maxBytes) {
            throw ContractError.of(ErrorCode.IMAGE_TOO_LARGE);
        }
        byte[] bytes;
        try (InputStream input = file.getInputStream()) {
            bytes = input.readNBytes(maxReadBytes);
        } catch (IOException ignored) {
            throw ContractError.of(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        }
        if (bytes.length > maxBytes) {
            throw ContractError.of(ErrorCode.IMAGE_TOO_LARGE);
        }
        return bytes;
    }

    private static int maximumReadBytes(long maxBytes) {
        try {
            return Math.toIntExact(Math.addExact(maxBytes, 1));
        } catch (ArithmeticException ignored) {
            throw ContractError.of(ErrorCode.CONFIGURATION_UNAVAILABLE);
        }
    }
}
