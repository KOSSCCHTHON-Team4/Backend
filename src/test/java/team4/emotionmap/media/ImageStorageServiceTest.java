package team4.emotionmap.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.media.ImageMediaType;
import team4.emotionmap.contracts.media.SanitizedImage;
import team4.emotionmap.contracts.media.StoredImageMeta;

class ImageStorageServiceTest {

    private static final int PARTIAL_BYTES_WRITTEN = 2;
    private static final String WRITE_FAILURE_MESSAGE = "synthetic-write-failure";
    private static final String CLOSE_FAILURE_MESSAGE = "synthetic-close-failure";
    private static final String CLEANUP_FAILURE_MESSAGE = "synthetic-cleanup-failure";

    @TempDir
    Path tempDir;

    @Test
    void storesCopiesAndDeletesOnlyTheIndependentOwnedFileUnderOpaqueKeys() throws Exception {
        ImageStorageService storage = storageFor(tempDir);
        byte[] bytes = pngBytes();

        StoredImageMeta stored = storage.store(new SanitizedImage(bytes, ImageMediaType.PNG, 2, 2));
        StoredImageMeta copied = storage.duplicateIndependent(stored.storageKey());

        assertThat(stored.storageKey()).matches("[0-9a-fA-F-]{36}\\.png");
        assertThat(copied.storageKey()).isNotEqualTo(stored.storageKey());
        assertThat(stored.sizeBytes()).isEqualTo(bytes.length);
        assertThat(copied.sizeBytes()).isEqualTo(bytes.length);
        assertThat(copied.width()).isEqualTo(2);
        assertThat(copied.height()).isEqualTo(2);
        assertThat(Files.readAllBytes(tempDir.resolve(stored.storageKey()))).containsExactly(bytes);
        assertThat(Files.readAllBytes(tempDir.resolve(copied.storageKey()))).containsExactly(bytes);

        byte[] changedOriginal = {9, 8};
        Files.write(tempDir.resolve(stored.storageKey()), changedOriginal);
        assertThat(Files.readAllBytes(tempDir.resolve(copied.storageKey()))).containsExactly(bytes);

        assertThat(storage.deleteUnreferenced(copied.storageKey())).isTrue();
        assertThat(Files.exists(tempDir.resolve(copied.storageKey()))).isFalse();
        assertThat(Files.readAllBytes(tempDir.resolve(stored.storageKey()))).containsExactly(changedOriginal);
    }
    @Test
    void managedStorageKeysProgressesAcrossBatchesWithoutDeletingReferencedCandidates() throws Exception {
        ImageStorageService storage = storageFor(tempDir);
        byte[] imageBytes = pngBytes();
        List<String> managedKeys = List.of(
                "11111111-1111-1111-1111-111111111111.png",
                "22222222-2222-2222-2222-222222222222.png",
                "33333333-3333-3333-3333-333333333333.png");
        for (String managedKey : managedKeys) {
            Files.write(tempDir.resolve(managedKey), imageBytes);
        }
        Path unfamiliar = tempDir.resolve("unfamiliar.bin");
        byte[] unfamiliarBytes = {4, 5, 6};
        Files.write(unfamiliar, unfamiliarBytes);
        Path symlink = tempDir.resolve("44444444-4444-4444-4444-444444444444.png");
        Files.createSymbolicLink(symlink, Path.of(managedKeys.get(0)));

        List<String> firstBatch = storage.managedStorageKeys(2);
        List<String> secondBatch = storage.managedStorageKeys(2);
        Set<String> observed = new HashSet<>(firstBatch);
        observed.addAll(secondBatch);

        assertThat(firstBatch.size()).isLessThanOrEqualTo(2);
        assertThat(secondBatch.size()).isLessThanOrEqualTo(2);
        assertThat(firstBatch).doesNotHaveDuplicates();
        assertThat(secondBatch).doesNotHaveDuplicates();
        assertThat(firstBatch).allMatch(managedKeys::contains);
        assertThat(secondBatch).allMatch(managedKeys::contains);
        assertThat(observed).containsExactlyInAnyOrderElementsOf(managedKeys);
        assertThat(observed).doesNotContain(symlink.getFileName().toString());
        assertThat(managedKeys).allMatch(key -> Files.isRegularFile(tempDir.resolve(key)));
        assertThat(Files.readAllBytes(unfamiliar)).containsExactly(unfamiliarBytes);
        assertThat(Files.isSymbolicLink(symlink)).isTrue();
        assertThat(Files.readAllBytes(symlink)).containsExactly(imageBytes);
    }

    @Test
    void lateStoreWriteFailureRemovesOnlyTheNewPartialFile() throws Exception {
        ImageStorageService storage = storageFor(tempDir);
        Path unrelated = tempDir.resolve("unrelated.bin");
        byte[] unrelatedBytes = {71, 72, 73};
        Files.write(unrelated, unrelatedBytes);
        byte[] imageBytes = pngBytes();
        FailureCapture capture = new FailureCapture();

        try (MockedStatic<FileChannel> channels = Mockito.mockStatic(FileChannel.class, Mockito.CALLS_REAL_METHODS)) {
            installPartialWriteFailure(channels, capture);

            ContractError error = catchThrowableOfType(ContractError.class,
                    () -> storage.store(new SanitizedImage(imageBytes, ImageMediaType.PNG, 2, 2)));

            assertSafeOutwardFailure(error, capture);
            assertFailureWasInjected(capture, imageBytes);
            assertThat(capture.target.toFile().exists()).isFalse();
            assertDirectoryContainsOnly(unrelated);
            assertThat(Files.readAllBytes(unrelated)).containsExactly(unrelatedBytes);
        }
    }

    @Test
    void lateCopyFailureRemovesOnlyTheNewPartialFileAndPreservesSource() throws Exception {
        ImageStorageService storage = storageFor(tempDir);
        byte[] sourceBytes = pngBytes();
        StoredImageMeta source = storage.store(new SanitizedImage(sourceBytes, ImageMediaType.PNG, 2, 2));
        Path sourcePath = tempDir.resolve(source.storageKey());
        Path unrelated = tempDir.resolve("unrelated.bin");
        byte[] unrelatedBytes = {81, 82, 83};
        Files.write(unrelated, unrelatedBytes);
        FailureCapture capture = new FailureCapture();

        try (MockedStatic<FileChannel> channels = Mockito.mockStatic(FileChannel.class, Mockito.CALLS_REAL_METHODS)) {
            installPartialWriteFailure(channels, capture);

            ContractError error = catchThrowableOfType(ContractError.class,
                    () -> storage.duplicateIndependent(source.storageKey()));

            assertSafeOutwardFailure(error, capture);
            assertFailureWasInjected(capture, sourceBytes);
            assertThat(capture.target.toFile().exists()).isFalse();
            assertDirectoryContainsOnly(sourcePath, unrelated);
            assertThat(Files.readAllBytes(sourcePath)).containsExactly(sourceBytes);
            assertThat(Files.readAllBytes(unrelated)).containsExactly(unrelatedBytes);
        }
    }

    @Test
    void cleanupFailureLeavesOnlyItsPartialFileAndEmitsAPathFreeLogWithoutThrowable() throws Exception {
        ImageStorageService storage = storageFor(tempDir);
        byte[] sourceBytes = pngBytes();
        StoredImageMeta source = storage.store(new SanitizedImage(sourceBytes, ImageMediaType.PNG, 2, 2));
        Path sourcePath = tempDir.resolve(source.storageKey());
        Path unrelated = tempDir.resolve("unrelated.bin");
        byte[] unrelatedBytes = {91, 92, 93};
        Files.write(unrelated, unrelatedBytes);
        FailureCapture capture = new FailureCapture();

        Logger logger = (Logger) LoggerFactory.getLogger(ImageStorageService.class);
        Level previousLevel = logger.getLevel();
        boolean previousAdditive = logger.isAdditive();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.ALL);
        logger.setAdditive(false);
        logger.addAppender(appender);
        try {
            try (MockedStatic<FileChannel> channels =
                         Mockito.mockStatic(FileChannel.class, Mockito.CALLS_REAL_METHODS);
                 MockedStatic<Files> files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
                installPartialWriteFailure(channels, capture);
                files.when(() -> Files.deleteIfExists(Mockito.any(Path.class))).thenAnswer(invocation -> {
                    Path candidate = invocation.getArgument(0, Path.class);
                    if (candidate.equals(capture.target)) {
                        throw new IOException(CLEANUP_FAILURE_MESSAGE + ":" + candidate);
                    }
                    return invocation.callRealMethod();
                });

                ContractError error = catchThrowableOfType(ContractError.class,
                        () -> storage.duplicateIndependent(source.storageKey()));

                assertSafeOutwardFailure(error, capture);
                assertFailureWasInjected(capture, sourceBytes);
                assertThat(capture.target.toFile().exists()).isTrue();
                assertDirectoryContainsOnly(sourcePath, unrelated, capture.target);
                assertThat(Files.readAllBytes(capture.target))
                        .containsExactly(Arrays.copyOf(sourceBytes, PARTIAL_BYTES_WRITTEN));
                assertThat(Files.readAllBytes(sourcePath)).containsExactly(sourceBytes);
                assertThat(Files.readAllBytes(unrelated)).containsExactly(unrelatedBytes);
                assertThat(appender.list).isNotEmpty();
                assertThat(appender.list.stream().allMatch(event -> isSafeCleanupEvent(event, capture))).isTrue();
            }
        } finally {
            if (capture.target != null) {
                Files.deleteIfExists(capture.target);
            }
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
            logger.setAdditive(previousAdditive);
        }

        assertThat(capture.target != null && !capture.target.toFile().exists()).isTrue();
        assertDirectoryContainsOnly(sourcePath, unrelated);
    }

    @Test
    void storageIoFailureIsPathFreeAndDoesNotExposeCause() throws Exception {
        Path rootFile = tempDir.resolve("not-a-directory");
        Files.write(rootFile, new byte[]{1});
        ImageStorageService storage = storageFor(rootFile);

        ContractError error = catchThrowableOfType(ContractError.class,
                () -> storage.store(new SanitizedImage(new byte[]{1}, ImageMediaType.JPEG, 1, 1)));

        assertThat(error.code()).isEqualTo(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        assertThat(error).hasNoCause();
        assertThat(error.getMessage()).doesNotContain(rootFile.toString());
        assertThat(Arrays.asList(tempDir.toFile().list())).containsExactly("not-a-directory");
    }

    private ImageStorageService storageFor(Path root) {
        ImageRootBinding rootBinding = Mockito.mock(ImageRootBinding.class);
        ImageFileFence fence = Mockito.mock(ImageFileFence.class);
        Mockito.when(rootBinding.requireReadableRoot()).thenReturn(root);
        Mockito.when(fence.requireWriterProtection()).thenReturn(root);
        Mockito.when(fence.requireCollectorProtection()).thenReturn(root);
        StorageProperties properties = new StorageProperties(root.toString(), 64L, Duration.ofMinutes(1), 10);
        return new ImageStorageService(properties, rootBinding, fence);
    }

    private void installPartialWriteFailure(MockedStatic<FileChannel> channels, FailureCapture capture) {
        channels.when(() -> FileChannel.open(Mockito.any(Path.class), Mockito.any(OpenOption[].class)))
                .thenAnswer(invocation -> {
                    Path target = invocation.getArgument(0, Path.class);
                    FileChannel delegate = (FileChannel) invocation.callRealMethod();
                    boolean createsNew = Arrays.stream(invocation.getArguments(), 1,
                                    invocation.getArguments().length)
                            .anyMatch(argument -> argument == StandardOpenOption.CREATE_NEW
                                    || argument instanceof OpenOption[] options
                                    && Arrays.asList(options).contains(StandardOpenOption.CREATE_NEW));
                    if (!createsNew) {
                        return delegate;
                    }
                    capture.target = target;
                    capture.targetCreated = Files.isRegularFile(target);
                    return new PartialFailureFileChannel(delegate, target, capture);
                });
    }

    private void assertDirectoryContainsOnly(Path... expected) throws IOException {
        List<String> actual;
        try (var entries = Files.list(tempDir)) {
            actual = entries.map(path -> path.getFileName().toString()).toList();
        }
        assertThat(actual.size()).isEqualTo(expected.length);
        for (Path expectedPath : expected) {
            assertThat(actual.stream()
                    .filter(name -> name.equals(expectedPath.getFileName().toString()))
                    .count()).isEqualTo(1);
        }
        assertThat(actual.stream()
                .filter(name -> Arrays.stream(expected)
                        .noneMatch(path -> name.equals(path.getFileName().toString())))
                .count()).isEqualTo(0);
    }

    private void assertSafeOutwardFailure(Throwable error, FailureCapture capture) {
        assertThat(error).isNotNull();
        assertThat(error).hasNoCause();
        assertThat(error.getSuppressed()).isEmpty();
        assertThat(isSafeText(error.getMessage(), capture)).isTrue();
    }

    private void assertFailureWasInjected(FailureCapture capture, byte[] expectedBytes) {
        assertThat(capture.targetCreated).isTrue();
        assertThat(capture.partialBytesBeforeFailure).isNotNull();
        assertThat(capture.partialBytesBeforeFailure.length > 0).isTrue();
        assertThat(capture.partialBytesBeforeFailure)
                .containsExactly(Arrays.copyOf(expectedBytes, PARTIAL_BYTES_WRITTEN));
        assertThat(capture.writeFailure).isNotNull();
        assertThat(capture.closeFailure).isNotNull();
        assertThat(capture.writeFailure.getSuppressed().length).isEqualTo(1);
        assertThat(capture.writeFailure.getSuppressed()[0] == capture.closeFailure).isTrue();
    }

    private boolean isSafeCleanupEvent(ILoggingEvent event, FailureCapture capture) {
        return event.getLevel() == Level.WARN
                && event.getFormattedMessage() != null
                && !event.getFormattedMessage().isBlank()
                && isSafeText(event.getFormattedMessage(), capture)
                && event.getThrowableProxy() == null;
    }

    private boolean isSafeText(String value, FailureCapture capture) {
        if (value == null || value.contains(WRITE_FAILURE_MESSAGE)
                || value.contains(CLOSE_FAILURE_MESSAGE)
                || value.contains(CLEANUP_FAILURE_MESSAGE)) {
            return false;
        }
        return capture == null || capture.target == null || !value.contains(capture.target.toString());
    }

    private static byte[] pngBytes() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, "png", output)).isTrue();
        return output.toByteArray();
    }

    private static final class FailureCapture {
        private Path target;
        private boolean targetCreated;
        private byte[] partialBytesBeforeFailure;
        private IOException writeFailure;
        private IOException closeFailure;
    }

    private static final class PartialFailureFileChannel extends FileChannel {
        private final FileChannel delegate;
        private final Path target;
        private final FailureCapture capture;

        private PartialFailureFileChannel(FileChannel delegate, Path target, FailureCapture capture) {
            this.delegate = delegate;
            this.target = target;
            this.capture = capture;
        }

        @Override
        public int read(ByteBuffer destination) throws IOException {
            return delegate.read(destination);
        }

        @Override
        public int read(ByteBuffer destination, long position) throws IOException {
            return delegate.read(destination, position);
        }

        @Override
        public long read(ByteBuffer[] destinations, int offset, int length) throws IOException {
            return delegate.read(destinations, offset, length);
        }

        @Override
        public int write(ByteBuffer source) throws IOException {
            int amount = Math.min(PARTIAL_BYTES_WRITTEN, source.remaining());
            ByteBuffer partial = source.duplicate();
            partial.limit(partial.position() + amount);
            int written = delegate.write(partial);
            source.position(source.position() + written);
            delegate.force(false);
            capture.partialBytesBeforeFailure = Files.readAllBytes(target);
            capture.writeFailure = new IOException(WRITE_FAILURE_MESSAGE + ":" + target);
            throw capture.writeFailure;
        }

        @Override
        public int write(ByteBuffer source, long position) throws IOException {
            return delegate.write(source, position);
        }

        @Override
        public long write(ByteBuffer[] sources, int offset, int length) throws IOException {
            return delegate.write(sources, offset, length);
        }

        @Override
        public long position() throws IOException {
            return delegate.position();
        }

        @Override
        public FileChannel position(long newPosition) throws IOException {
            delegate.position(newPosition);
            return this;
        }

        @Override
        public long size() throws IOException {
            return delegate.size();
        }

        @Override
        public FileChannel truncate(long size) throws IOException {
            delegate.truncate(size);
            return this;
        }

        @Override
        public void force(boolean metadata) throws IOException {
            delegate.force(metadata);
        }

        @Override
        public long transferTo(long position, long count, java.nio.channels.WritableByteChannel target)
                throws IOException {
            return delegate.transferTo(position, count, target);
        }

        @Override
        public long transferFrom(java.nio.channels.ReadableByteChannel source, long position, long count)
                throws IOException {
            return delegate.transferFrom(source, position, count);
        }

        @Override
        public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
            return delegate.map(mode, position, size);
        }

        @Override
        public FileLock lock(long position, long size, boolean shared) throws IOException {
            return delegate.lock(position, size, shared);
        }

        @Override
        public FileLock tryLock(long position, long size, boolean shared) throws IOException {
            return delegate.tryLock(position, size, shared);
        }

        @Override
        protected void implCloseChannel() throws IOException {
            IOException closeFailure = new IOException(CLOSE_FAILURE_MESSAGE + ":" + target);
            try {
                delegate.close();
            } finally {
                capture.closeFailure = closeFailure;
            }
            throw closeFailure;
        }
    }
}
