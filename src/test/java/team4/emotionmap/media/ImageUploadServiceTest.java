package team4.emotionmap.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import team4.emotionmap.account.User;
import team4.emotionmap.account.UserRepository;
import team4.emotionmap.contracts.config.AuthConfig;
import team4.emotionmap.contracts.config.ServiceConfig;
import team4.emotionmap.contracts.config.ServiceConfigSource;
import team4.emotionmap.contracts.config.ServiceLimits;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.geo.GeoPoint;
import team4.emotionmap.contracts.media.ImageLimits;
import team4.emotionmap.contracts.media.ImageMediaType;
import team4.emotionmap.contracts.media.SanitizedImage;
import team4.emotionmap.contracts.media.StoredImageMeta;
import team4.emotionmap.media.dto.ImageUploadResponse;

class ImageUploadServiceTest {

    private final ImageStorageService storage = mock(ImageStorageService.class);
    private final ImageUploadRepository uploads = mock(ImageUploadRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ImageSanitizer sanitizer = mock(ImageSanitizer.class);
    private final ServiceConfigSource configSource = mock(ServiceConfigSource.class);
    private final ImageUploadService service = new ImageUploadService(storage, uploads, users, sanitizer, configSource);
    private final UUID ownerId = UUID.randomUUID();

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void exactByteLimitIsReadFromStreamEvenWhenDeclaredSizeIsSmaller() throws Exception {
        ServiceLimits limits = limits(4, 37);
        when(configSource.current()).thenReturn(config(limits));
        when(users.findByIdForUpdate(ownerId)).thenReturn(java.util.Optional.of(activeUser()));

        byte[] payload = {10, 20, 30, 40};
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(1L);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(payload));
        SanitizedImage sanitized = new SanitizedImage(new byte[]{1, 2}, ImageMediaType.JPEG, 1, 1);
        StoredImageMeta stored = new StoredImageMeta("image-key.jpg", ImageMediaType.JPEG, 2, 1, 1);
        when(sanitizer.sanitize(eq(payload), eq(ImageLimits.from(limits)))).thenReturn(sanitized);
        when(storage.store(sanitized)).thenReturn(stored);
        doAnswer(invocation -> invocation.getArgument(0)).when(uploads).save(any(ImageUpload.class));
        TransactionSynchronizationManager.initSynchronization();

        ImageUploadResponse response = service.upload(ownerId, file);

        verify(sanitizer).sanitize(eq(payload), eq(ImageLimits.from(limits)));
        assertThat(response.mediaType()).isEqualTo(ImageMediaType.JPEG.mimeType());
        assertThat(response.sizeBytes()).isEqualTo(2);
        ArgumentCaptor<ImageUpload> saved = ArgumentCaptor.forClass(ImageUpload.class);
        verify(uploads).save(saved.capture());
        assertThat(Duration.between(saved.getValue().getCreatedAt(), saved.getValue().getExpiresAt()))
                .isEqualTo(Duration.ofSeconds(37));
    }

    @Test
    void actualStreamOverLimitIsRejectedAfterOnlyBPlusOneBytes() throws Exception {
        ServiceLimits limits = limits(4, 37);
        when(configSource.current()).thenReturn(config(limits));
        when(users.findByIdForUpdate(ownerId)).thenReturn(java.util.Optional.of(activeUser()));
        TrackingInputStream input = new TrackingInputStream(new byte[]{1, 2, 3, 4, 5});
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(1L);
        when(file.getInputStream()).thenReturn(input);

        ContractError error = catchThrowableOfType(ContractError.class, () -> service.upload(ownerId, file));

        assertThat(error.code()).isEqualTo(ErrorCode.IMAGE_TOO_LARGE);
        assertThat(input.consumed()).isEqualTo(5);
        assertThat(input.maximumRequestedRead()).isEqualTo(5);
        verify(sanitizer, never()).sanitize(any(), any());
        verify(storage, never()).store(any());
    }

    @Test
    void knownRollbackDeletesStoredFileButUnknownAndCommittedOutcomesPreserveIt() throws Exception {
        for (int completionStatus : new int[]{TransactionSynchronization.STATUS_UNKNOWN,
                TransactionSynchronization.STATUS_COMMITTED}) {
            StoredUploadResult result = uploadForSynchronization();
            result.synchronization().afterCompletion(completionStatus);
            TransactionSynchronizationManager.clearSynchronization();
        }
        verify(storage, never()).delete(anyString());

        StoredUploadResult result = uploadForSynchronization();
        result.synchronization().afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(storage).delete(result.storageKey());
    }

    @Test
    void attachabilityPrioritizesAttachedConflictThenExpiryBeforeStorageLookup() {
        ImageUpload attachedAndExpired = imageUpload(UUID.randomUUID(), ImageUploadStatus.ATTACHED,
                Instant.now().minusSeconds(60));
        when(uploads.findByIdForUpdate(attachedAndExpired.getId()))
                .thenReturn(java.util.Optional.of(attachedAndExpired));

        ResponseStatusException attachedError = catchThrowableOfType(ResponseStatusException.class,
                () -> service.requireAttachable(ownerId, attachedAndExpired.getId()));
        assertThat(attachedError.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ImageUpload expired = imageUpload(UUID.randomUUID(), ImageUploadStatus.STAGED, Instant.now().minusSeconds(60));
        when(uploads.findByIdForUpdate(expired.getId())).thenReturn(java.util.Optional.of(expired));

        ResponseStatusException expiredError = catchThrowableOfType(ResponseStatusException.class,
                () -> service.requireAttachable(ownerId, expired.getId()));
        assertThat(expiredError.getStatusCode()).isEqualTo(HttpStatus.GONE);
        verify(storage, never()).load(anyString());

        ImageUpload usable = imageUpload(UUID.randomUUID(), ImageUploadStatus.STAGED, Instant.now().plusSeconds(60));
        when(uploads.findByIdForUpdate(usable.getId())).thenReturn(java.util.Optional.of(usable));

        assertThat(service.requireAttachable(ownerId, usable.getId())).isSameAs(usable);
        verify(storage).load(usable.getStoragePath());
    }

    private StoredUploadResult uploadForSynchronization() throws Exception {
        ServiceLimits limits = limits(4, 37);
        when(configSource.current()).thenReturn(config(limits));
        when(users.findByIdForUpdate(ownerId)).thenReturn(java.util.Optional.of(activeUser()));
        byte[] payload = {1, 2, 3, 4};
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn((long) payload.length);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(payload));
        SanitizedImage sanitized = new SanitizedImage(new byte[]{1}, ImageMediaType.JPEG, 1, 1);
        String key = UUID.randomUUID() + ".jpg";
        when(sanitizer.sanitize(any(), any())).thenReturn(sanitized);
        when(storage.store(sanitized)).thenReturn(new StoredImageMeta(key, ImageMediaType.JPEG, 1, 1, 1));
        doAnswer(invocation -> invocation.getArgument(0)).when(uploads).save(any(ImageUpload.class));
        TransactionSynchronizationManager.initSynchronization();

        service.upload(ownerId, file);
        return new StoredUploadResult(key, TransactionSynchronizationManager.getSynchronizations().get(0));
    }

    private ImageUpload imageUpload(UUID imageId, ImageUploadStatus status, Instant expiresAt) {
        return ImageUpload.builder()
                .id(imageId)
                .ownerId(ownerId)
                .storagePath(imageId + ".jpg")
                .mediaType(ImageMediaType.JPEG.mimeType())
                .sizeBytes(1L)
                .width(1)
                .height(1)
                .status(status)
                .createdAt(expiresAt.minusSeconds(1))
                .expiresAt(expiresAt)
                .build();
    }

    private User activeUser() {
        return User.builder().id(ownerId).accessStatus(team4.emotionmap.contracts.account.AccountStatus.ACTIVE).build();
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

        private TrackingInputStream(byte[] bytes) {
            this.delegate = new ByteArrayInputStream(bytes);
        }

        @Override
        public int read() {
            int value = delegate.read();
            if (value >= 0) {
                consumed++;
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            maximumRequestedRead = Math.max(maximumRequestedRead, length);
            int read = delegate.read(bytes, offset, length);
            if (read > 0) {
                consumed += read;
            }
            return read;
        }

        int consumed() {
            return consumed;
        }

        int maximumRequestedRead() {
            return maximumRequestedRead;
        }
    }
}
