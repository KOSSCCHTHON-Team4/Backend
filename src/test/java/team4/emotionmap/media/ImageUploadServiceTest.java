package team4.emotionmap.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import team4.emotionmap.account.User;
import team4.emotionmap.account.UserRepository;
import team4.emotionmap.contracts.account.AccountStatus;
import team4.emotionmap.contracts.config.AuthConfig;
import team4.emotionmap.contracts.config.ServiceConfig;
import team4.emotionmap.contracts.config.ServiceConfigSource;
import team4.emotionmap.contracts.config.ServiceLimits;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.geo.GeoPoint;
import team4.emotionmap.contracts.media.ImageFileLifecycle;
import team4.emotionmap.contracts.media.ImageLimits;
import team4.emotionmap.contracts.media.ImageMediaType;
import team4.emotionmap.contracts.media.SanitizedImage;
import team4.emotionmap.contracts.media.StoredImageMeta;
import team4.emotionmap.contracts.request.RequestCoordinator;
import team4.emotionmap.media.dto.ImageUploadResponse;

class ImageUploadServiceTest {
    private static final Instant NOW = Instant.parse("2031-02-03T04:05:06Z");
    private static final byte[] FINGERPRINT_PREFIX =
            "emotionmap:idempotency:POST:/v1/images:v1\0".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private final ImageStorageService storage = Mockito.mock(ImageStorageService.class);
    private final ImageUploadRepository uploads = Mockito.mock(ImageUploadRepository.class);
    private final UserRepository users = Mockito.mock(UserRepository.class);
    private final ImageSanitizer sanitizer = Mockito.mock(ImageSanitizer.class);
    private final ServiceConfigSource configSource = Mockito.mock(ServiceConfigSource.class);
    private final RequestCoordinator coordinator = Mockito.mock(RequestCoordinator.class);
    private final ImageFileLifecycle lifecycle = Mockito.mock(ImageFileLifecycle.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final UUID ownerId = UUID.randomUUID();

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void boundedOriginalBytesDriveFingerprintAndSanitizedWrite() throws Exception {
        ServiceLimits limits = limits(4, 37);
        when(configSource.current()).thenReturn(config(limits));
        UUID key = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        byte[] original = {10, 20, 30, 40};
        SanitizedImage sanitized = new SanitizedImage(new byte[]{1, 2}, ImageMediaType.JPEG, 1, 1);
        StoredImageMeta stored = new StoredImageMeta(UUID.randomUUID() + ".jpg", ImageMediaType.JPEG, 2, 1, 1);
        when(users.findByIdForUpdate(ownerId)).thenReturn(Optional.of(activeUser()));
        when(users.findById(ownerId)).thenReturn(Optional.of(activeUser()));
        when(sanitizer.sanitize(eq(original), eq(ImageLimits.from(limits)))).thenReturn(sanitized);
        when(storage.store(sanitized)).thenReturn(stored);
        ImageUpload persisted = persisted(imageId, stored, NOW.plusSeconds(37));
        when(uploads.saveAndFlush(any(ImageUpload.class))).thenReturn(persisted);
        when(uploads.findById(imageId)).thenReturn(Optional.of(persisted));
        RequestCoordinator.Claim claim = claim(key);
        when(coordinator.claim(any(), any())).thenReturn(new RequestCoordinator.Claimed(claim));
        when(coordinator.complete(any(), any())).thenAnswer(invocation -> {
            Supplier<RequestCoordinator.ResourceRef> create = invocation.getArgument(1);
            return new RequestCoordinator.Completion(RequestCoordinator.CompletionKind.CREATED, create.get());
        });

        TransactionSynchronizationManager.initSynchronization();
        ImageUploadService.UploadResult result = service(validProperties()).upload(ownerId, key, file(original, 1));

        assertThat(result.kind()).isEqualTo(RequestCoordinator.CompletionKind.CREATED);
        assertThat(result.response().sizeBytes()).isEqualTo(2);
        ArgumentCaptor<RequestCoordinator.Fingerprint> fingerprint = ArgumentCaptor.forClass(RequestCoordinator.Fingerprint.class);
        verify(coordinator).claim(eq(new RequestCoordinator.Scope(ownerId, RequestCoordinator.Route.IMAGES, key)),
                fingerprint.capture());
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(FINGERPRINT_PREFIX);
        digest.update(original);
        assertThat(fingerprint.getValue().version()).isEqualTo(1);
        verify(lifecycle).protectWritesInCurrentTransaction();
        assertThat(fingerprint.getValue().digest()).containsExactly(digest.digest());
        ArgumentCaptor<ImageUpload> saved = ArgumentCaptor.forClass(ImageUpload.class);
        verify(uploads).saveAndFlush(saved.capture());
        assertThat(Duration.between(saved.getValue().getCreatedAt(), saved.getValue().getExpiresAt()))
                .isEqualTo(Duration.ofSeconds(37));
    }

    @Test
    void actualStreamOverLimitIsRejectedAfterOnlyBPlusOneBytes() throws Exception {
        when(configSource.current()).thenReturn(config(limits(4, 37)));
        TrackingInputStream input = new TrackingInputStream(new byte[]{1, 2, 3, 4, 5});
        ContractError error = catchThrowableOfType(ContractError.class,
                () -> service(validProperties()).upload(ownerId, UUID.randomUUID(), file(input, 1)));
        assertThat(error.code()).isEqualTo(ErrorCode.IMAGE_TOO_LARGE);
        assertThat(input.consumed()).isEqualTo(5);
        assertThat(input.maximumRequestedRead()).isEqualTo(5);
        verify(coordinator, never()).claim(any(), any());
        verify(storage, never()).store(any());
    }

    @Test
    void replayUsesPersistedReceiptAndDistinguishesExpiredStagedFromAttached() throws Exception {
        when(configSource.current()).thenReturn(config(limits(8, 37)));
        UUID key = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        ImageUpload attached = imageUpload(imageId, ImageUploadStatus.ATTACHED, NOW.minusSeconds(60));
        when(coordinator.claim(any(), any())).thenReturn(new RequestCoordinator.Replay(
                new RequestCoordinator.ResourceRef(RequestCoordinator.Route.IMAGES, imageId)));
        when(users.findById(ownerId)).thenReturn(Optional.of(activeUser()));
        when(uploads.findById(imageId)).thenReturn(Optional.of(attached));
        ImageUploadService.UploadResult result = service(validProperties()).upload(ownerId, key, file(new byte[]{1}, 1));
        assertThat(result.kind()).isEqualTo(RequestCoordinator.CompletionKind.REPLAY);
        assertThat(result.response().expiresAt()).isEqualTo(attached.getExpiresAt());
        verify(sanitizer, never()).sanitize(any(), any());
        verify(storage, never()).store(any());
        verify(coordinator, never()).complete(any(), any());

        UUID expiredId = UUID.randomUUID();
        when(coordinator.claim(any(), any())).thenReturn(new RequestCoordinator.Replay(
                new RequestCoordinator.ResourceRef(RequestCoordinator.Route.IMAGES, expiredId)));
        when(uploads.findById(expiredId)).thenReturn(Optional.of(imageUpload(expiredId,
                ImageUploadStatus.STAGED, NOW.minusSeconds(1))));
        ContractError expired = catchThrowableOfType(ContractError.class,
                () -> service(validProperties()).upload(ownerId, key, file(new byte[]{1}, 1)));
        assertThat(expired.code()).isEqualTo(ErrorCode.IMAGE_UPLOAD_EXPIRED);
    }

    @Test
    void missingCleanupConfigurationBlocksNewWriteButNotReplayReceipt() throws Exception {
        when(configSource.current()).thenReturn(config(limits(8, 37)));
        UUID key = UUID.randomUUID();
        RequestCoordinator.Claim claim = claim(key);
        when(coordinator.claim(any(), any())).thenReturn(new RequestCoordinator.Claimed(claim));
        when(users.findById(ownerId)).thenReturn(Optional.of(activeUser()));
        ContractError error = catchThrowableOfType(ContractError.class,
                () -> service(new StorageProperties("unused", 8_192L, null, null))
                        .upload(ownerId, key, file(new byte[]{1}, 1)));
        assertThat(error.code()).isEqualTo(ErrorCode.CONFIGURATION_UNAVAILABLE);
        verify(coordinator).abandon(claim);
        verify(sanitizer, never()).sanitize(any(), any());
        verify(storage, never()).store(any());

        UUID imageId = UUID.randomUUID();
        when(coordinator.claim(any(), any())).thenReturn(new RequestCoordinator.Replay(
                new RequestCoordinator.ResourceRef(RequestCoordinator.Route.IMAGES, imageId)));
        when(uploads.findById(imageId)).thenReturn(Optional.of(imageUpload(imageId,
                ImageUploadStatus.STAGED, NOW.plusSeconds(60))));
        ImageUploadService.UploadResult replay = service(new StorageProperties("unused", 8_192L, null, null))
                .upload(ownerId, key, file(new byte[]{1}, 1));
        assertThat(replay.kind()).isEqualTo(RequestCoordinator.CompletionKind.REPLAY);
    }

    @Test
    void rollbackCleanupUsesLifecycleAndUnknownOrCommittedOutcomesPreserveFile() throws Exception {
        for (int status : new int[]{TransactionSynchronization.STATUS_UNKNOWN, TransactionSynchronization.STATUS_COMMITTED}) {
            StoredUploadResult upload = uploadForSynchronization();
            upload.synchronization().afterCompletion(status);
            TransactionSynchronizationManager.clearSynchronization();
        }
        verify(lifecycle, never()).deleteIfUnreferenced(any(String.class));
        StoredUploadResult rollback = uploadForSynchronization();
        rollback.synchronization().afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(lifecycle).deleteIfUnreferenced(rollback.storageKey());
    }

    @Test
    void attachabilityPrioritizesAttachedConflictAndExpiryBeforeMetadataLookup() {
        ImageUpload attached = imageUpload(UUID.randomUUID(), ImageUploadStatus.ATTACHED, NOW.minusSeconds(60));
        when(uploads.findByIdForUpdate(attached.getId())).thenReturn(Optional.of(attached));
        ContractError attachedError = catchThrowableOfType(ContractError.class,
                () -> service(validProperties()).requireAttachable(ownerId, attached.getId()));
        assertThat(attachedError.code()).isEqualTo(ErrorCode.IMAGE_ALREADY_ATTACHED);
        verify(storage, never()).describe(any(String.class));

        ImageUpload expired = imageUpload(UUID.randomUUID(), ImageUploadStatus.STAGED, NOW.minusSeconds(1));
        when(uploads.findByIdForUpdate(expired.getId())).thenReturn(Optional.of(expired));
        ContractError expiredError = catchThrowableOfType(ContractError.class,
                () -> service(validProperties()).requireAttachable(ownerId, expired.getId()));
        assertThat(expiredError.code()).isEqualTo(ErrorCode.IMAGE_UPLOAD_EXPIRED);

        ImageUpload usable = imageUpload(UUID.randomUUID(), ImageUploadStatus.STAGED, NOW.plusSeconds(60));
        when(uploads.findByIdForUpdate(usable.getId())).thenReturn(Optional.of(usable));
        when(storage.describe(usable.getStoragePath())).thenReturn(Optional.of(new StoredImageMeta(
                usable.getStoragePath(), ImageMediaType.JPEG, 1, 1, 1)));
        assertThat(service(validProperties()).requireAttachable(ownerId, usable.getId())).isSameAs(usable);
        verify(storage).describe(usable.getStoragePath());
    }

    private StoredUploadResult uploadForSynchronization() throws Exception {
        ServiceLimits limits = limits(4, 37);
        when(configSource.current()).thenReturn(config(limits));
        UUID key = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        byte[] payload = {1, 2, 3, 4};
        SanitizedImage sanitized = new SanitizedImage(new byte[]{1}, ImageMediaType.JPEG, 1, 1);
        String storageKey = UUID.randomUUID() + ".jpg";
        StoredImageMeta stored = new StoredImageMeta(storageKey, ImageMediaType.JPEG, 1, 1, 1);
        when(users.findByIdForUpdate(ownerId)).thenReturn(Optional.of(activeUser()));
        when(users.findById(ownerId)).thenReturn(Optional.of(activeUser()));
        when(sanitizer.sanitize(any(), any())).thenReturn(sanitized);
        when(storage.store(sanitized)).thenReturn(stored);
        ImageUpload persisted = persisted(imageId, stored, NOW.plusSeconds(37));
        when(uploads.saveAndFlush(any(ImageUpload.class))).thenReturn(persisted);
        when(uploads.findById(imageId)).thenReturn(Optional.of(persisted));
        RequestCoordinator.Claim claim = claim(key);
        when(coordinator.claim(any(), any())).thenReturn(new RequestCoordinator.Claimed(claim));
        Mockito.doAnswer(invocation -> {
            Supplier<RequestCoordinator.ResourceRef> create = invocation.getArgument(1);
            return new RequestCoordinator.Completion(RequestCoordinator.CompletionKind.CREATED, create.get());
        }).when(coordinator).complete(any(), any());
        TransactionSynchronizationManager.initSynchronization();
        service(validProperties()).upload(ownerId, key, file(payload, payload.length));
        return new StoredUploadResult(storageKey, TransactionSynchronizationManager.getSynchronizations().getFirst());
    }

    private ImageUploadService service(StorageProperties properties) {
        return new ImageUploadService(storage, uploads, users, sanitizer, configSource, coordinator,
                lifecycle, properties, clock);
    }

    private StorageProperties validProperties() {
        return new StorageProperties("unused", 8_192L, Duration.ofMinutes(1), 10);
    }

    private MultipartFile file(byte[] bytes, long declaredSize) throws Exception {
        MultipartFile file = Mockito.mock(MultipartFile.class);
        when(file.getSize()).thenReturn(declaredSize);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(bytes));
        return file;
    }

    private MultipartFile file(InputStream input, long declaredSize) throws Exception {
        MultipartFile file = Mockito.mock(MultipartFile.class);
        when(file.getSize()).thenReturn(declaredSize);
        when(file.getInputStream()).thenReturn(input);
        return file;
    }

    private RequestCoordinator.Claim claim(UUID key) {
        return new RequestCoordinator.Claim(new RequestCoordinator.Scope(ownerId, RequestCoordinator.Route.IMAGES, key),
                new RequestCoordinator.Fingerprint(1, new byte[32]), UUID.randomUUID(), 1, NOW.plusSeconds(60));
    }

    private ImageUpload persisted(UUID id, StoredImageMeta stored, Instant expiresAt) {
        return ImageUpload.builder().id(id).ownerId(ownerId).storagePath(stored.storageKey())
                .mediaType(stored.mediaType().mimeType()).sizeBytes(stored.sizeBytes()).width(stored.width())
                .height(stored.height()).status(ImageUploadStatus.STAGED).createdAt(NOW).expiresAt(expiresAt).build();
    }

    private ImageUpload imageUpload(UUID id, ImageUploadStatus status, Instant expiresAt) {
        return ImageUpload.builder().id(id).ownerId(ownerId).storagePath(id + ".jpg")
                .mediaType(ImageMediaType.JPEG.mimeType()).sizeBytes(1L).width(1).height(1).status(status)
                .createdAt(NOW.minusSeconds(1)).expiresAt(expiresAt).build();
    }

    private User activeUser() {
        return User.builder().id(ownerId).accessStatus(AccountStatus.ACTIVE).build();
    }

    private static ServiceLimits limits(long imageBytes, int ttlSeconds) {
        return new ServiceLimits(100, 100, 100, imageBytes, 100, 100, 10_000,
                ttlSeconds, 60, 10, 10, 20, 20);
    }

    private static ServiceConfig config(ServiceLimits limits) {
        return new ServiceConfig("test", 100, new GeoPoint(1, 1), limits, new AuthConfig(3600, 5, 60));
    }

    private record StoredUploadResult(String storageKey, TransactionSynchronization synchronization) { }

    private static final class TrackingInputStream extends InputStream {
        private final ByteArrayInputStream delegate;
        private int consumed;
        private int maximumRequestedRead;
        private TrackingInputStream(byte[] bytes) { this.delegate = new ByteArrayInputStream(bytes); }
        @Override public int read() { int value = delegate.read(); if (value >= 0) consumed++; return value; }
        @Override public int read(byte[] bytes, int offset, int length) {
            maximumRequestedRead = Math.max(maximumRequestedRead, length);
            int read = delegate.read(bytes, offset, length); if (read > 0) consumed += read; return read;
        }
        int consumed() { return consumed; }
        int maximumRequestedRead() { return maximumRequestedRead; }
    }
}
