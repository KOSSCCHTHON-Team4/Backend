package team4.emotionmap.media;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import team4.emotionmap.contracts.media.ImageFileLifecycle;
import team4.emotionmap.contracts.memory.MemoryImageReferences;

/** Coordinates reference-safe expiry and garbage collection with the filesystem fences. */
@Service
public class ImageFileLifecycleService implements ImageFileLifecycle {

    private final PlatformTransactionManager transactionManager;
    private final StorageProperties storageProperties;
    private final ImageFileFence imageFileFence;
    private final ImageStorageService imageStorageService;
    private final ImageUploadRepository imageUploadRepository;
    private final MemoryImageReferences memoryImageReferences;
    private final Clock clock;

    private final Object expiryCursorMonitor = new Object();
    private ExpiryCursor expiryCursor;

    public ImageFileLifecycleService(PlatformTransactionManager transactionManager,
                                     StorageProperties storageProperties, ImageFileFence imageFileFence,
                                     ImageStorageService imageStorageService,
                                     ImageUploadRepository imageUploadRepository,
                                     MemoryImageReferences memoryImageReferences, Clock clock) {
        this.transactionManager = transactionManager;
        this.storageProperties = storageProperties;
        this.imageFileFence = imageFileFence;
        this.imageStorageService = imageStorageService;
        this.imageUploadRepository = imageUploadRepository;
        this.memoryImageReferences = memoryImageReferences;
        this.clock = clock;
    }

    @Override
    public void protectWritesInCurrentTransaction() {
        storageProperties.requireUsableCleanupConfiguration();
        imageFileFence.protectWritesInCurrentTransaction();
    }

    @Override
    public CleanupResult deleteIfUnreferenced(String storageKey) {
        storageProperties.requireUsableCleanupConfiguration();
        if (!imageStorageService.isManagedStorageKey(storageKey)) {
            return CleanupResult.ABSENT;
        }
        CleanupResult result = newReadCommittedTransaction().execute(status -> {
            if (!imageFileFence.tryProtectCollectionInCurrentTransaction()) {
                return CleanupResult.BUSY;
            }
            // These are deliberately separate statements after both exclusive fences are held.
            if (imageUploadRepository.existsByStoragePath(storageKey)
                    || memoryImageReferences.existsReference(storageKey)) {
                return CleanupResult.REFERENCED;
            }
            return imageStorageService.deleteUnreferenced(storageKey)
                    ? CleanupResult.DELETED : CleanupResult.ABSENT;
        });
        return result == null ? CleanupResult.BUSY : result;
    }

    /**
     * Commits only the tombstone/reference release. Physical cleanup is intentionally a later,
     * separate collector transaction.
     *
     * <p>The cursor is process-local and serialized here, not shared through the database. It
     * advances only after the bounded transaction commits. A committed exhausted scan clears it
     * for the next invocation to wrap to the head; a process restart does the same. Lock
     * contention, repeated restarts, or churn can still defer a row.
     */
    List<String> expireDueUploads() {
        storageProperties.requireUsableCleanupConfiguration();
        synchronized (expiryCursorMonitor) {
            ExpiryCursor cursor = expiryCursor;
            ExpiryBatch expired = newReadCommittedTransaction().execute(status -> {
                imageFileFence.protectWritesInCurrentTransaction();
                Instant now = clock.instant();
                List<ImageUpload> dueUploads = imageUploadRepository.findDueStagedAfterForUpdateSkipLocked(now,
                        cursor == null ? null : cursor.expiresAt(), cursor == null ? null : cursor.id(),
                        storageProperties.requiredCleanupBatchSize());
                if (dueUploads.isEmpty()) {
                    // SKIP LOCKED is not proof of exhaustion; retain the cursor while later due rows remain.
                    boolean laterDue = cursor != null && imageUploadRepository.existsDueStagedAfter(now,
                            cursor.expiresAt(), cursor.id());
                    return new ExpiryBatch(List.of(), laterDue ? cursor : null);
                }

                ImageUpload last = dueUploads.getLast();
                ExpiryCursor nextCursor = new ExpiryCursor(last.getExpiresAt(), last.getId());
                List<String> releasedKeys = new ArrayList<>();
                for (ImageUpload upload : dueUploads) {
                    String storageKey = upload.getStoragePath();
                    if (storageKey == null || upload.getAttachedMemoryId() != null
                            || upload.getStatus() != ImageUploadStatus.STAGED || upload.getExpiresAt() == null
                            || upload.getExpiresAt().isAfter(now)) {
                        continue;
                    }
                    if (memoryImageReferences.existsReference(storageKey)) {
                        // Preserve an inconsistent but still referenced staged record rather than clearing its key.
                        continue;
                    }
                    if (upload.expireIfDue(now)) {
                        releasedKeys.add(storageKey);
                    }
                }
                imageUploadRepository.flush();
                return new ExpiryBatch(List.copyOf(releasedKeys), nextCursor);
            });
            if (expired == null) {
                return List.of();
            }
            // TransactionTemplate returns only after commit; a rollback/exception leaves the prior cursor intact.
            expiryCursor = expired.nextCursor();
            return expired.releasedKeys();
        }
    }

    private record ExpiryCursor(Instant expiresAt, UUID id) {
    }

    private record ExpiryBatch(List<String> releasedKeys, ExpiryCursor nextCursor) {
    }

    private TransactionTemplate newReadCommittedTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return template;
    }
}
