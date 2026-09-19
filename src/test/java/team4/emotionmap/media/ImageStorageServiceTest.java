package team4.emotionmap.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
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
        ImageStorageService storage = new ImageStorageService(new StorageProperties(tempDir.toString(), 64L));
        byte[] bytes = {1, 2, 3, 4};

        StoredImageMeta stored = storage.store(new SanitizedImage(bytes, ImageMediaType.PNG, 2, 2));
        String copiedKey = storage.copy(stored.storageKey());

        assertThat(stored.storageKey()).matches("[0-9a-fA-F-]{36}\\.png");
        assertThat(copiedKey).isNotEqualTo(stored.storageKey());
        assertThat(stored.sizeBytes()).isEqualTo(bytes.length);
        assertThat(Files.readAllBytes(tempDir.resolve(stored.storageKey()))).containsExactly(bytes);
        assertThat(Files.readAllBytes(tempDir.resolve(copiedKey))).containsExactly(bytes);

        byte[] changedOriginal = {9, 8};
        Files.write(tempDir.resolve(stored.storageKey()), changedOriginal);
        assertThat(Files.readAllBytes(tempDir.resolve(copiedKey))).containsExactly(bytes);

        storage.delete(copiedKey);
        assertThat(Files.exists(tempDir.resolve(copiedKey))).isFalse();
        assertThat(Files.readAllBytes(tempDir.resolve(stored.storageKey()))).containsExactly(changedOriginal);
    }

    @Test
    void lateStoreWriteFailureRemovesOnlyTheNewPartialFile() throws Exception {
        ImageStorageService storage = new ImageStorageService(new StorageProperties(tempDir.toString(), 64L));
        Path unrelated = tempDir.resolve("unrelated.bin");
        byte[] unrelatedBytes = {71, 72, 73};
        Files.write(unrelated, unrelatedBytes);
        byte[] imageBytes = {1, 2, 3, 4, 5};
        FailureCapture capture = new FailureCapture();

        try (MockedStatic<Files> files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
            installPartialWriteFailure(files, capture);

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
        ImageStorageService storage = new ImageStorageService(new StorageProperties(tempDir.toString(), 64L));
        byte[] sourceBytes = {11, 12, 13, 14, 15};
        StoredImageMeta source = storage.store(new SanitizedImage(sourceBytes, ImageMediaType.PNG, 2, 2));
        Path sourcePath = tempDir.resolve(source.storageKey());
        Path unrelated = tempDir.resolve("unrelated.bin");
        byte[] unrelatedBytes = {81, 82, 83};
        Files.write(unrelated, unrelatedBytes);
        FailureCapture capture = new FailureCapture();

        try (MockedStatic<Files> files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
            installPartialWriteFailure(files, capture);

            IllegalStateException error = catchThrowableOfType(IllegalStateException.class,
                    () -> storage.copy(source.storageKey()));

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
        ImageStorageService storage = new ImageStorageService(new StorageProperties(tempDir.toString(), 64L));
        byte[] sourceBytes = {21, 22, 23, 24, 25};
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
            try (MockedStatic<Files> files = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
                installPartialWriteFailure(files, capture);
                files.when(() -> Files.deleteIfExists(Mockito.any(Path.class))).thenAnswer(invocation -> {
                    Path candidate = invocation.getArgument(0, Path.class);
                    if (candidate.equals(capture.target)) {
                        throw new IOException(CLEANUP_FAILURE_MESSAGE + ":" + candidate);
                    }
                    return invocation.callRealMethod();
                });

                IllegalStateException error = catchThrowableOfType(IllegalStateException.class,
                        () -> storage.copy(source.storageKey()));

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
        ImageStorageService storage = new ImageStorageService(new StorageProperties(rootFile.toString(), 64L));

        ContractError error = catchThrowableOfType(ContractError.class,
                () -> storage.store(new SanitizedImage(new byte[]{1}, ImageMediaType.JPEG, 1, 1)));

        assertThat(error.code()).isEqualTo(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        assertThat(error).hasNoCause();
        assertThat(error.getMessage()).doesNotContain(rootFile.toString());
        assertThat(Arrays.asList(tempDir.toFile().list())).containsExactly("not-a-directory");
    }

    private void installPartialWriteFailure(MockedStatic<Files> files, FailureCapture capture) {
        files.when(() -> Files.newOutputStream(Mockito.any(Path.class), Mockito.any(OpenOption[].class)))
                .thenAnswer(invocation -> {
                    Path target = invocation.getArgument(0, Path.class);
                    OutputStream delegate = (OutputStream) invocation.callRealMethod();
                    capture.target = target;
                    capture.targetCreated = target.toFile().isFile();
                    return new PartialFailureOutputStream(target, delegate, capture);
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
        return event.getLevel() == Level.ERROR
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

    private static final class FailureCapture {
        private Path target;
        private boolean targetCreated;
        private byte[] partialBytesBeforeFailure;
        private IOException writeFailure;
        private IOException closeFailure;
    }

    private static final class PartialFailureOutputStream extends OutputStream {
        private final Path target;
        private final OutputStream delegate;
        private final FailureCapture capture;

        private PartialFailureOutputStream(Path target, OutputStream delegate, FailureCapture capture) {
            this.target = target;
            this.delegate = delegate;
            this.capture = capture;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            delegate.write(bytes, offset, Math.min(PARTIAL_BYTES_WRITTEN, length));
            delegate.flush();
            capture.partialBytesBeforeFailure = readActualBytes(target);
            capture.writeFailure = new IOException(WRITE_FAILURE_MESSAGE + ":" + target);
            throw capture.writeFailure;
        }

        @Override
        public void write(int value) throws IOException {
            delegate.write(value);
            delegate.flush();
            capture.partialBytesBeforeFailure = readActualBytes(target);
            capture.writeFailure = new IOException(WRITE_FAILURE_MESSAGE + ":" + target);
            throw capture.writeFailure;
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
            capture.closeFailure = new IOException(CLOSE_FAILURE_MESSAGE + ":" + target);
            throw capture.closeFailure;
        }

        private byte[] readActualBytes(Path path) throws IOException {
            try (FileInputStream input = new FileInputStream(path.toFile())) {
                return input.readAllBytes();
            }
        }
    }
}
