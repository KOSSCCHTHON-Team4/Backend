package team4.emotionmap.media;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
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
import team4.emotionmap.contracts.media.ImageFileLifecycle;
import team4.emotionmap.contracts.media.ImageLimits;
import team4.emotionmap.contracts.media.SanitizedImage;
import team4.emotionmap.contracts.media.StoredImageMeta;
import team4.emotionmap.contracts.request.RequestCoordinator;
import team4.emotionmap.media.dto.ImageUploadResponse;

@Service
@Slf4j
public class ImageUploadService {
    private static final byte[] IMAGE_FINGERPRINT_PREFIX =
            "emotionmap:idempotency:POST:/v1/images:v1\0".getBytes(StandardCharsets.UTF_8);

    private final ImageStorageService imageStorageService;
    private final ImageUploadRepository imageUploadRepository;
    private final UserRepository userRepository;
    private final ImageSanitizer imageSanitizer;
    private final ServiceConfigSource serviceConfigSource;
    private final RequestCoordinator requestCoordinator;
    private final ImageFileLifecycle imageFileLifecycle;
    private final StorageProperties storageProperties;
    private final Clock clock;

    public ImageUploadService(ImageStorageService imageStorageService, ImageUploadRepository imageUploadRepository,
                              UserRepository userRepository, ImageSanitizer imageSanitizer,
                              ServiceConfigSource serviceConfigSource, RequestCoordinator requestCoordinator,
                              ImageFileLifecycle imageFileLifecycle, StorageProperties storageProperties, Clock clock) {
        this.imageStorageService = imageStorageService;
        this.imageUploadRepository = imageUploadRepository;
        this.userRepository = userRepository;
        this.imageSanitizer = imageSanitizer;
        this.serviceConfigSource = serviceConfigSource;
        this.requestCoordinator = requestCoordinator;
        this.imageFileLifecycle = imageFileLifecycle;
        this.storageProperties = storageProperties;
        this.clock = clock;
    }

    /**
     * Claims durable request ownership only after the original request bytes have been bounded and
     * fingerprinted. The surrounding HTTP call deliberately owns no business transaction.
     */
    public UploadResult upload(UUID ownerId, UUID key, MultipartFile file) {
        ServiceLimits serviceLimits = serviceConfigSource.current().limits();
        ImageLimits imageLimits = ImageLimits.from(serviceLimits);
        byte[] original = readBounded(file, imageLimits.maxBytes());
        RequestCoordinator.Scope scope = new RequestCoordinator.Scope(ownerId, RequestCoordinator.Route.IMAGES, key);
        RequestCoordinator.Admission admission = requestCoordinator.claim(scope, fingerprint(original));
        if (admission instanceof RequestCoordinator.Replay replay) {
            return receiptFor(ownerId, replay.resource(), RequestCoordinator.CompletionKind.REPLAY);
        }

        RequestCoordinator.Claim claim = ((RequestCoordinator.Claimed) admission).claim();
        SanitizedImage sanitized;
        try {
            requireCurrentOwner(ownerId);
            // Replays never reach this point, so a missing cleanup setting cannot suppress a receipt.
            storageProperties.requireUsableCleanupConfiguration();
            sanitized = imageSanitizer.sanitize(original, imageLimits);
        } catch (RuntimeException error) {
            abandonKnownPreCompletionFailure(claim);
            throw error;
        }

        RequestCoordinator.Completion completion = requestCoordinator.complete(claim,
                () -> persistNewUpload(ownerId, sanitized, serviceLimits));
        return receiptFor(ownerId, completion.resource(), completion.kind());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ImageUpload requireAttachable(UUID ownerId, UUID imageId) {
        ImageUpload upload = imageUploadRepository.findByIdForUpdate(imageId)
                .filter(image -> image.getOwnerId().equals(ownerId))
                .orElseThrow(() -> ContractError.of(ErrorCode.IMAGE_NOT_FOUND));
        if (upload.getStatus() == ImageUploadStatus.ATTACHED) {
            throw ContractError.of(ErrorCode.IMAGE_ALREADY_ATTACHED);
        }
        if (upload.getStatus() != ImageUploadStatus.STAGED || upload.getExpiresAt() == null
                || !upload.getExpiresAt().isAfter(clock.instant())) {
            throw ContractError.of(ErrorCode.IMAGE_UPLOAD_EXPIRED);
        }
        StoredImageMeta actual = upload.getStoragePath() == null ? null
                : imageStorageService.describe(upload.getStoragePath()).orElse(null);
        if (!matchesStoredMetadata(upload, actual)) {
            throw ContractError.of(ErrorCode.IMAGE_FILE_UNAVAILABLE);
        }
        return upload;
    }
    private static boolean matchesStoredMetadata(ImageUpload upload, StoredImageMeta actual) {
        return actual != null && Objects.equals(upload.getMediaType(), actual.mediaType().mimeType())
                && Objects.equals(upload.getSizeBytes(), actual.sizeBytes())
                && Objects.equals(upload.getWidth(), actual.width())
                && Objects.equals(upload.getHeight(), actual.height());
    }


    private RequestCoordinator.ResourceRef persistNewUpload(UUID ownerId, SanitizedImage sanitized,
                                                              ServiceLimits serviceLimits) {
        User owner = userRepository.findByIdForUpdate(ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
        AccountAccessService.requireActive(owner);

        imageFileLifecycle.protectWritesInCurrentTransaction();
        StoredImageMeta stored = imageStorageService.store(sanitized);
        registerKnownRollbackCleanup(stored.storageKey());

        Instant now = clock.instant();
        ImageUpload upload = imageUploadRepository.saveAndFlush(ImageUpload.builder()
                .ownerId(ownerId).storagePath(stored.storageKey()).mediaType(stored.mediaType().mimeType())
                .sizeBytes(stored.sizeBytes()).width(stored.width()).height(stored.height())
                .createdAt(now).expiresAt(expiryAt(now, serviceLimits.imageUploadTtlSeconds())).build());
        return new RequestCoordinator.ResourceRef(RequestCoordinator.Route.IMAGES, upload.getId());
    }

    private UploadResult receiptFor(UUID ownerId, RequestCoordinator.ResourceRef resource,
                                    RequestCoordinator.CompletionKind kind) {
        if (resource.route() != RequestCoordinator.Route.IMAGES) {
            throw ContractError.of(ErrorCode.SERVICE_UNAVAILABLE);
        }
        requireCurrentOwner(ownerId);
        ImageUpload upload = imageUploadRepository.findById(resource.id())
                .filter(candidate -> candidate.getOwnerId().equals(ownerId))
                .orElseThrow(() -> ContractError.of(ErrorCode.IMAGE_NOT_FOUND));
        if (upload.getStatus() != ImageUploadStatus.ATTACHED
                && (upload.getStatus() != ImageUploadStatus.STAGED || upload.getExpiresAt() == null
                || !upload.getExpiresAt().isAfter(clock.instant()))) {
            throw ContractError.of(ErrorCode.IMAGE_UPLOAD_EXPIRED);
        }
        return new UploadResult(ImageUploadResponse.from(upload), kind);
    }

    private void requireCurrentOwner(UUID ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
        AccountAccessService.requireActive(owner);
    }

    private void registerKnownRollbackCleanup(String storageKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                // STATUS_UNKNOWN can have committed; only a known rollback may enqueue reconciliation.
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        imageFileLifecycle.deleteIfUnreferenced(storageKey);
                    } catch (RuntimeException ignored) {
                        log.warn("Failed to reconcile a rolled-back image upload");
                    }
                }
            }
        });
    }

    private void abandonKnownPreCompletionFailure(RequestCoordinator.Claim claim) {
        try {
            requestCoordinator.abandon(claim);
        } catch (RuntimeException ignored) {
            // The processing lease is safer than guessing whether another owner completed it.
            log.warn("Failed to abandon a pre-completion image upload claim");
        }
    }

    private static RequestCoordinator.Fingerprint fingerprint(byte[] original) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(IMAGE_FINGERPRINT_PREFIX);
            digest.update(original);
            return new RequestCoordinator.Fingerprint(1, digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static Instant expiryAt(Instant now, int ttlSeconds) {
        try {
            return now.plusSeconds(ttlSeconds);
        } catch (DateTimeException | ArithmeticException ignored) {
            throw ContractError.of(ErrorCode.CONFIGURATION_UNAVAILABLE);
        }
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

    public record UploadResult(ImageUploadResponse response, RequestCoordinator.CompletionKind kind) {
    }
}
