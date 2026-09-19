package team4.emotionmap.media;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.media.ImageMediaType;
import team4.emotionmap.contracts.media.LocalImageStore;
import team4.emotionmap.contracts.media.SanitizedImage;
import team4.emotionmap.contracts.media.StoredImageMeta;

/** Local, binding-gated implementation of the shared file-store contract. */
@Service
@Slf4j
public class ImageStorageService implements LocalImageStore {

    private static final Pattern STORAGE_KEY = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(?:jpg|png)$");
    private static final int COPY_BUFFER_BYTES = 16 * 1024;

    private final StorageProperties storageProperties;
    private final ImageRootBinding imageRootBinding;
    private final ImageFileFence imageFileFence;

    private final Object managedStorageTraversalMonitor = new Object();
    private DirectoryStream<Path> managedStorageEntries;
    private Iterator<Path> managedStorageIterator;
    private Path managedStorageRoot;

    public ImageStorageService(StorageProperties storageProperties, ImageRootBinding imageRootBinding,
                               ImageFileFence imageFileFence) {
        this.storageProperties = storageProperties;
        this.imageRootBinding = imageRootBinding;
        this.imageFileFence = imageFileFence;
    }

    @Override
    public StoredImageMeta store(SanitizedImage image) {
        Objects.requireNonNull(image, "image");
        storageProperties.requireUsableCleanupConfiguration();
        Path root = imageFileFence.requireWriterProtection();
        String key = UUID.randomUUID() + "." + image.mediaType().extension();
        Path target = target(root, key);
        boolean created = false;
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            created = true;
            writeFully(channel, ByteBuffer.wrap(image.bytes()));
            channel.force(true);
            forceDirectory(root);
            return new StoredImageMeta(key, image.mediaType(), image.sizeBytes(), image.width(), image.height());
        } catch (IOException ignored) {
            if (created) {
                removePartial(root, target);
            }
            throw ContractError.of(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        }
    }

    @Override
    public Optional<StoredImageMeta> describe(String storageKey) {
        Path root = imageRootBinding.requireReadableRoot();
        Path target = safeTarget(root, storageKey);
        if (target == null) {
            return Optional.empty();
        }
        try {
            BasicFileAttributes attributes = Files.readAttributes(target, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || attributes.isSymbolicLink() || attributes.size() < 1) {
                return Optional.empty();
            }
            try (InputStream input = Files.newInputStream(target, StandardOpenOption.READ)) {
                ImageDimensions dimensions = readHeader(input);
                return Optional.of(new StoredImageMeta(storageKey, dimensions.mediaType, attributes.size(),
                        dimensions.width, dimensions.height));
            }
        } catch (NoSuchFileException ignored) {
            return Optional.empty();
        } catch (IOException ignored) {
            throw ContractError.of(ErrorCode.IMAGE_FILE_UNAVAILABLE);
        }
    }

    @Override
    public InputStream open(String storageKey) {
        Path root = imageRootBinding.requireReadableRoot();
        Path target = safeTarget(root, storageKey);
        if (target == null) {
            throw ContractError.of(ErrorCode.RESOURCE_NOT_FOUND);
        }
        try {
            BasicFileAttributes attributes = Files.readAttributes(target, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
                throw ContractError.of(ErrorCode.RESOURCE_NOT_FOUND);
            }
            return Files.newInputStream(target, StandardOpenOption.READ);
        } catch (NoSuchFileException ignored) {
            throw ContractError.of(ErrorCode.RESOURCE_NOT_FOUND);
        } catch (ContractError error) {
            throw error;
        } catch (IOException ignored) {
            throw ContractError.of(ErrorCode.IMAGE_FILE_UNAVAILABLE);
        }
    }

    @Override
    public StoredImageMeta duplicateIndependent(String sourceStorageKey) {
        storageProperties.requireUsableCleanupConfiguration();
        Path root = imageFileFence.requireWriterProtection();
        Path source = safeTarget(root, sourceStorageKey);
        if (source == null) {
            throw ContractError.of(ErrorCode.RESOURCE_NOT_FOUND);
        }

        StoredImageMeta sourceMeta = describeWithinWriterFence(root, sourceStorageKey, source)
                .orElseThrow(() -> ContractError.of(ErrorCode.RESOURCE_NOT_FOUND));
        String copyKey = UUID.randomUUID() + "." + sourceMeta.mediaType().extension();
        Path target = target(root, copyKey);
        boolean created = false;
        try (FileChannel input = FileChannel.open(source, StandardOpenOption.READ);
             FileChannel output = FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            created = true;
            long copied = copyBytes(input, output);
            if (copied != sourceMeta.sizeBytes()) {
                throw new IOException("Image file changed during copy");
            }
            output.force(true);
            forceDirectory(root);
            return new StoredImageMeta(copyKey, sourceMeta.mediaType(), copied, sourceMeta.width(),
                    sourceMeta.height());
        } catch (NoSuchFileException ignored) {
            if (created) {
                removePartial(root, target);
            }
            throw ContractError.of(ErrorCode.RESOURCE_NOT_FOUND);
        } catch (IOException ignored) {
            if (created) {
                removePartial(root, target);
            }
            throw ContractError.of(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        }
    }

    @Override
    public boolean deleteUnreferenced(String storageKey) {
        Path root = imageFileFence.requireCollectorProtection();
        Path target = safeTarget(root, storageKey);
        if (target == null) {
            return false;
        }
        try {
            BasicFileAttributes attributes = Files.readAttributes(target, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
                return false;
            }
            boolean deleted = Files.deleteIfExists(target);
            if (deleted) {
                forceDirectory(root);
            }
            return deleted;
        } catch (NoSuchFileException ignored) {
            return false;
        } catch (IOException ignored) {
            throw ContractError.of(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        }
    }

    /**
     * Returns candidates from one bounded, incremental direct-directory traversal.
     *
     * <p>The binding is revalidated before every batch. A newly opened traversal is retained only
     * until it is exhausted, reset after a traversal failure, or closed during bean destruction.
     */
    List<String> managedStorageKeys(int maximum) {
        if (maximum < 1) {
            return List.of();
        }
        synchronized (managedStorageTraversalMonitor) {
            try {
                Path root = imageRootBinding.requireReadableRoot();
                if (!root.equals(managedStorageRoot)) {
                    resetManagedStorageTraversal();
                }
                if (managedStorageEntries == null) {
                    managedStorageEntries = Files.newDirectoryStream(root);
                    managedStorageIterator = managedStorageEntries.iterator();
                    managedStorageRoot = root;
                }

                List<String> storageKeys = new ArrayList<>();
                while (storageKeys.size() < maximum && managedStorageIterator.hasNext()) {
                    Path entry = managedStorageIterator.next();
                    if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)
                            || Files.isSymbolicLink(entry)) {
                        continue;
                    }
                    Path fileName = entry.getFileName();
                    if (fileName != null) {
                        String storageKey = fileName.toString();
                        if (isManagedStorageKey(storageKey)) {
                            storageKeys.add(storageKey);
                        }
                    }
                }
                if (!managedStorageIterator.hasNext()) {
                    resetManagedStorageTraversal();
                }
                return List.copyOf(storageKeys);
            } catch (IOException | DirectoryIteratorException error) {
                resetManagedStorageTraversal();
                throw ContractError.of(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
            } catch (RuntimeException error) {
                resetManagedStorageTraversal();
                throw error;
            }
        }
    }

    @PreDestroy
    private void closeManagedStorageTraversal() {
        synchronized (managedStorageTraversalMonitor) {
            resetManagedStorageTraversal();
        }
    }

    private void resetManagedStorageTraversal() {
        DirectoryStream<Path> entries = managedStorageEntries;
        managedStorageEntries = null;
        managedStorageIterator = null;
        managedStorageRoot = null;
        if (entries == null) {
            return;
        }
        try {
            entries.close();
        } catch (IOException ignored) {
            log.warn("Managed image storage traversal could not be closed");
        }
    }

    boolean isManagedStorageKey(String storageKey) {
        return storageKey != null && STORAGE_KEY.matcher(storageKey).matches();
    }

    private Optional<StoredImageMeta> describeWithinWriterFence(Path root, String storageKey, Path source) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(source, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || attributes.isSymbolicLink() || attributes.size() < 1) {
                return Optional.empty();
            }
            try (InputStream input = Files.newInputStream(source, StandardOpenOption.READ)) {
                ImageDimensions dimensions = readHeader(input);
                return Optional.of(new StoredImageMeta(storageKey, dimensions.mediaType, attributes.size(),
                        dimensions.width, dimensions.height));
            }
        } catch (NoSuchFileException ignored) {
            return Optional.empty();
        } catch (IOException ignored) {
            throw ContractError.of(ErrorCode.IMAGE_FILE_UNAVAILABLE);
        }
    }

    private static Path target(Path root, String storageKey) {
        Path target = safeTarget(root, storageKey);
        if (target == null) {
            throw ContractError.of(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        }
        return target;
    }

    private static Path safeTarget(Path root, String storageKey) {
        if (storageKey == null || !STORAGE_KEY.matcher(storageKey).matches()) {
            return null;
        }
        Path target = root.resolve(storageKey).normalize();
        return target.startsWith(root) ? target : null;
    }

    private static long copyBytes(FileChannel input, FileChannel output) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(COPY_BUFFER_BYTES);
        long copied = 0;
        while (true) {
            int read = input.read(buffer);
            if (read < 0) {
                return copied;
            }
            if (read == 0) {
                continue;
            }
            buffer.flip();
            writeFully(output, buffer);
            buffer.clear();
            copied = Math.addExact(copied, read);
        }
    }

    private static void writeFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    private static void removePartial(Path root, Path target) {
        try {
            if (Files.deleteIfExists(target)) {
                forceDirectory(root);
            }
        } catch (IOException ignored) {
            log.warn("Failed to remove a partial image file");
        }
    }

    private static ImageDimensions readHeader(InputStream input) throws IOException {
        int first = requiredByte(input);
        int second = requiredByte(input);
        if (first == 0xFF && second == 0xD8) {
            return readJpegHeader(input);
        }
        if (first == 0x89 && second == 0x50) {
            return readPngHeader(input);
        }
        throw new IOException("Unsupported image header");
    }

    private static ImageDimensions readPngHeader(InputStream input) throws IOException {
        byte[] remainingSignature = readExactly(input, 6);
        if (remainingSignature[0] != 0x4E || remainingSignature[1] != 0x47
                || remainingSignature[2] != 0x0D || remainingSignature[3] != 0x0A
                || remainingSignature[4] != 0x1A || remainingSignature[5] != 0x0A) {
            throw new IOException("Invalid PNG header");
        }
        int ihdrLength = readInt(input);
        if (ihdrLength != 13 || requiredByte(input) != 'I' || requiredByte(input) != 'H'
                || requiredByte(input) != 'D' || requiredByte(input) != 'R') {
            throw new IOException("Invalid PNG IHDR");
        }
        return new ImageDimensions(ImageMediaType.PNG, positiveDimension(readInt(input)),
                positiveDimension(readInt(input)));
    }

    private static ImageDimensions readJpegHeader(InputStream input) throws IOException {
        while (true) {
            int prefix;
            do {
                prefix = requiredByte(input);
            } while (prefix != 0xFF);
            int marker;
            do {
                marker = requiredByte(input);
            } while (marker == 0xFF);
            if (marker == 0x00 || marker == 0xD9 || marker == 0xDA) {
                throw new IOException("JPEG has no frame header");
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                continue;
            }
            int length = (requiredByte(input) << 8) | requiredByte(input);
            if (length < 2) {
                throw new IOException("Invalid JPEG segment");
            }
            if (isStartOfFrame(marker)) {
                if (length < 8) {
                    throw new IOException("Invalid JPEG frame header");
                }
                requiredByte(input); // precision
                int height = (requiredByte(input) << 8) | requiredByte(input);
                int width = (requiredByte(input) << 8) | requiredByte(input);
                return new ImageDimensions(ImageMediaType.JPEG, positiveDimension(width), positiveDimension(height));
            }
            skipExactly(input, length - 2);
        }
    }

    private static boolean isStartOfFrame(int marker) {
        return switch (marker) {
            case 0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7,
                    0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF -> true;
            default -> false;
        };
    }

    private static int positiveDimension(int value) throws IOException {
        if (value < 1) {
            throw new IOException("Image dimensions are invalid");
        }
        return value;
    }

    private static int readInt(InputStream input) throws IOException {
        return (requiredByte(input) << 24) | (requiredByte(input) << 16)
                | (requiredByte(input) << 8) | requiredByte(input);
    }

    private static int requiredByte(InputStream input) throws IOException {
        int value = input.read();
        if (value < 0) {
            throw new IOException("Image header ended unexpectedly");
        }
        return value;
    }

    private static byte[] readExactly(InputStream input, int length) throws IOException {
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new IOException("Image header ended unexpectedly");
        }
        return bytes;
    }

    private static void skipExactly(InputStream input, int length) throws IOException {
        int remaining = length;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped > 0) {
                remaining -= (int) skipped;
            } else {
                requiredByte(input);
                remaining--;
            }
        }
    }

    private record ImageDimensions(ImageMediaType mediaType, int width, int height) {
    }
}
