package team4.emotionmap.media;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import team4.emotionmap.contracts.error.ContractError;

/** Schedules bounded expiry and GC only when the explicit cleanup settings are usable. */
@Component
@Slf4j
public class ImageCleanupService implements SmartLifecycle {

    private final StorageProperties storageProperties;
    private final ImageFileLifecycleService imageFileLifecycleService;
    private final ImageStorageService imageStorageService;
    private final Object lifecycleMonitor = new Object();

    private volatile boolean running;
    private ScheduledExecutorService executor;

    public ImageCleanupService(StorageProperties storageProperties,
                               ImageFileLifecycleService imageFileLifecycleService,
                               ImageStorageService imageStorageService) {
        this.storageProperties = storageProperties;
        this.imageFileLifecycleService = imageFileLifecycleService;
        this.imageStorageService = imageStorageService;
    }

    @Override
    public void start() {
        synchronized (lifecycleMonitor) {
            if (running || !storageProperties.hasUsableCleanupConfiguration()) {
                return;
            }
            ScheduledExecutorService created = null;
            try {
                long intervalNanos = storageProperties.requiredCleanupIntervalNanos();
                created = Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "emotionmap-image-cleanup");
                    thread.setDaemon(true);
                    return thread;
                });
                created.scheduleWithFixedDelay(this::runSafely, intervalNanos, intervalNanos,
                        TimeUnit.NANOSECONDS);
                executor = created;
                running = true;
            } catch (RuntimeException ignored) {
                if (created != null) {
                    created.shutdownNow();
                }
                executor = null;
                log.warn("Image cleanup scheduling is unavailable");
            }
        }
    }

    @Override
    public void stop() {
        synchronized (lifecycleMonitor) {
            running = false;
            if (executor != null) {
                executor.shutdown();
                executor = null;
            }
        }
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    void cleanupOnce() {
        if (!storageProperties.hasUsableCleanupConfiguration()) {
            return;
        }
        int batchSize = storageProperties.requiredCleanupBatchSize();
        List<String> expired = imageFileLifecycleService.expireDueUploads();
        for (String storageKey : expired) {
            imageFileLifecycleService.deleteIfUnreferenced(storageKey);
        }

        for (String storageKey : imageStorageService.managedStorageKeys(batchSize)) {
            imageFileLifecycleService.deleteIfUnreferenced(storageKey);
        }
    }

    private void runSafely() {
        try {
            cleanupOnce();
        } catch (ContractError error) {
            // Binding, database, or lock failures preserve files and are retried on a later schedule.
            log.warn("Image cleanup skipped: {}", error.code());
        } catch (RuntimeException ignored) {
            log.warn("Image cleanup skipped due to an internal failure");
        }
    }
}
