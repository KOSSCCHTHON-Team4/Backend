package team4.emotionmap.media;

import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;

/**
 * Couples a PostgreSQL transaction-scoped advisory lock with a process-shared filesystem lock.
 *
 * <p>Each canonical root uses one JVM channel. The in-JVM accounting keeps shared users from
 * releasing an OS lock while another transaction is still doing I/O, and avoids overlapping Java
 * file-lock attempts on that channel.
 */
@Component
@Slf4j
public class ImageFileFence {

    static final String LOCK_FILE_NAME = ".emotionmap-storage.lock";
    private static final String ADVISORY_NAMESPACE = "emotionmap:image-files:v1";
    private static final Object ROOT_LOCK_MONITOR = new Object();
    private static final Map<Path, RootLockState> ROOT_LOCKS = new HashMap<>();

    private final EntityManager entityManager;
    private final StorageProperties storageProperties;

    public ImageFileFence(EntityManager entityManager, StorageProperties storageProperties) {
        this.entityManager = entityManager;
        this.storageProperties = storageProperties;
    }

    /** Acquires the writer half of both fences and retains it until transaction completion. */
    public void protectWritesInCurrentTransaction() {
        requireSynchronizedTransaction();
        TransactionGuard existing = currentGuard();
        if (existing != null) {
            if (existing.mode == Mode.WRITER) {
                return;
            }
            throw ContractError.of(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        }

        Path root = ImageRootBinding.requireBoundInCurrentTransaction(entityManager, storageProperties,
                ErrorCode.CONFIGURATION_UNAVAILABLE);
        if (!tryAdvisoryLock(true)) {
            throw ContractError.of(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        }

        RootGuard rootGuard;
        try {
            rootGuard = tryAcquireShared(root);
        } catch (IOException ignored) {
            throw ContractError.of(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        }
        if (rootGuard == null) {
            throw ContractError.of(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        }
        registerGuard(new TransactionGuard(root, Mode.WRITER, rootGuard));
    }

    /**
     * Acquires the collector half of both fences. A false result is only ordinary lock contention;
     * binding and I/O failures remain fail-closed exceptions.
     */
    boolean tryProtectCollectionInCurrentTransaction() {
        requireSynchronizedTransaction();
        if (currentGuard() != null) {
            return false;
        }

        Path root = ImageRootBinding.requireBoundInCurrentTransaction(entityManager, storageProperties,
                ErrorCode.CONFIGURATION_UNAVAILABLE);
        if (!tryAdvisoryLock(false)) {
            return false;
        }

        RootGuard rootGuard;
        try {
            rootGuard = tryAcquireExclusive(root);
        } catch (IOException ignored) {
            throw ContractError.of(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        }
        if (rootGuard == null) {
            return false;
        }
        registerGuard(new TransactionGuard(root, Mode.COLLECTOR, rootGuard));
        return true;
    }

    Path requireWriterProtection() {
        TransactionGuard guard = currentGuard();
        if (guard == null || guard.mode != Mode.WRITER) {
            throw ContractError.of(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        }
        return guard.root;
    }

    Path requireCollectorProtection() {
        TransactionGuard guard = currentGuard();
        if (guard == null || guard.mode != Mode.COLLECTOR) {
            throw ContractError.of(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        }
        return guard.root;
    }

    /**
     * Empty-root activation creates the fixed lock file exactly once, then keeps this exclusive
     * lock through the binding transaction completion.
     */
    RootGuard acquireActivationExclusive(Path root) {
        synchronized (ROOT_LOCK_MONITOR) {
            Path lockFile = root.resolve(LOCK_FILE_NAME);
            if (Files.exists(lockFile, LinkOption.NOFOLLOW_LINKS)) {
                throw ContractError.of(ErrorCode.INVALID_REQUEST);
            }

            try {
                FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.READ, StandardOpenOption.WRITE);
                channel.force(true);
                forceDirectory(root);
                RootLockState state = new RootLockState(channel);
                ROOT_LOCKS.put(root, state);
                FileLock lock = tryLock(channel, false);
                if (lock == null) {
                    throw ContractError.of(ErrorCode.INVALID_REQUEST);
                }
                state.osLock = lock;
                state.exclusive = true;
                return new RootGuard(state, Mode.COLLECTOR);
            } catch (FileAlreadyExistsException ignored) {
                throw ContractError.of(ErrorCode.INVALID_REQUEST);
            } catch (IOException ignored) {
                throw ContractError.of(ErrorCode.CONFIGURATION_UNAVAILABLE);
            }
        }
    }

    void releaseAfterTransactionCompletion(RootGuard rootGuard) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            rootGuard.close();
            throw ContractError.of(ErrorCode.CONFIGURATION_UNAVAILABLE);
        }
        try {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public int getOrder() {
                    return Ordered.HIGHEST_PRECEDENCE;
                }

                @Override
                public void afterCompletion(int status) {
                    rootGuard.close();
                }
            });
        } catch (RuntimeException exception) {
            rootGuard.close();
            throw exception;
        }
    }

    private void requireSynchronizedTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw ContractError.of(ErrorCode.CONFIGURATION_UNAVAILABLE);
        }
    }

    private boolean tryAdvisoryLock(boolean shared) {
        String function = shared ? "pg_try_advisory_xact_lock_shared" : "pg_try_advisory_xact_lock";
        try {
            Object result = entityManager.createNativeQuery("select " + function
                    + "(hashtextextended('" + ADVISORY_NAMESPACE + "', 0))").getSingleResult();
            return Boolean.TRUE.equals(result);
        } catch (RuntimeException ignored) {
            throw ContractError.of(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        }
    }

    private void registerGuard(TransactionGuard guard) {
        try {
            TransactionSynchronizationManager.bindResource(this, guard);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public int getOrder() {
                    // Rollback cleanup registrations happen after this guard is acquired.
                    return Ordered.HIGHEST_PRECEDENCE;
                }

                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(ImageFileFence.this);
                    guard.rootGuard.close();
                }
            });
        } catch (RuntimeException exception) {
            TransactionSynchronizationManager.unbindResourceIfPossible(this);
            guard.rootGuard.close();
            throw exception;
        }
    }

    private TransactionGuard currentGuard() {
        Object resource = TransactionSynchronizationManager.getResource(this);
        return resource instanceof TransactionGuard guard ? guard : null;
    }

    private static RootGuard tryAcquireShared(Path root) throws IOException {
        synchronized (ROOT_LOCK_MONITOR) {
            RootLockState state = stateForBoundRoot(root);
            if (state.exclusive) {
                return null;
            }
            if (state.sharedUsers == 0) {
                FileLock lock = tryLock(state.channel, true);
                if (lock == null) {
                    return null;
                }
                state.osLock = lock;
            }
            state.sharedUsers++;
            return new RootGuard(state, Mode.WRITER);
        }
    }

    private static RootGuard tryAcquireExclusive(Path root) throws IOException {
        synchronized (ROOT_LOCK_MONITOR) {
            RootLockState state = stateForBoundRoot(root);
            if (state.exclusive || state.sharedUsers != 0) {
                return null;
            }
            FileLock lock = tryLock(state.channel, false);
            if (lock == null) {
                return null;
            }
            state.osLock = lock;
            state.exclusive = true;
            return new RootGuard(state, Mode.COLLECTOR);
        }
    }

    private static RootLockState stateForBoundRoot(Path root) throws IOException {
        RootLockState known = ROOT_LOCKS.get(root);
        if (known != null) {
            return known;
        }
        Path lockFile = root.resolve(LOCK_FILE_NAME);
        if (!Files.isRegularFile(lockFile, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(lockFile)) {
            throw new IOException("Storage lock is unavailable");
        }
        RootLockState created = new RootLockState(FileChannel.open(lockFile,
                StandardOpenOption.READ, StandardOpenOption.WRITE));
        ROOT_LOCKS.put(root, created);
        return created;
    }

    private static FileLock tryLock(FileChannel channel, boolean shared) throws IOException {
        try {
            return channel.tryLock(0L, Long.MAX_VALUE, shared);
        } catch (OverlappingFileLockException ignored) {
            return null;
        }
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    private static void release(RootLockState state, Mode mode) {
        synchronized (ROOT_LOCK_MONITOR) {
            try {
                if (mode == Mode.WRITER) {
                    if (state.sharedUsers > 0 && --state.sharedUsers == 0) {
                        releaseOsLock(state);
                    }
                } else if (state.exclusive) {
                    state.exclusive = false;
                    releaseOsLock(state);
                }
            } catch (IOException ignored) {
                log.warn("Failed to release image storage filesystem lock");
            }
        }
    }

    private static void releaseOsLock(RootLockState state) throws IOException {
        if (state.osLock != null) {
            state.osLock.release();
            state.osLock = null;
        }
    }

    static final class RootGuard implements AutoCloseable {
        private final RootLockState state;
        private final Mode mode;
        private final AtomicBoolean closed = new AtomicBoolean();

        private RootGuard(RootLockState state, Mode mode) {
            this.state = state;
            this.mode = mode;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                release(state, mode);
            }
        }
    }

    private static final class RootLockState {
        private final FileChannel channel;
        private FileLock osLock;
        private int sharedUsers;
        private boolean exclusive;

        private RootLockState(FileChannel channel) {
            this.channel = channel;
        }
    }

    private record TransactionGuard(Path root, Mode mode, RootGuard rootGuard) {
    }

    private enum Mode {
        WRITER, COLLECTOR
    }
}
