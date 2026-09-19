package team4.emotionmap.media;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.media.SanitizedImage;
import team4.emotionmap.contracts.media.StoredImageMeta;

@Service
@Slf4j
public class ImageStorageService {
    private final Path root;

    public ImageStorageService(StorageProperties properties) {
        this.root = Path.of(properties.uploadDir()).toAbsolutePath().normalize();
    }

    public StoredImageMeta store(SanitizedImage image) {
        String key = UUID.randomUUID() + "." + image.mediaType().extension();
        Path target = root.resolve(key);
        boolean targetCreated = false;
        try {
            Files.createDirectories(root);
            try (OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                targetCreated = true;
                output.write(image.bytes());
            }
            return new StoredImageMeta(key, image.mediaType(), image.sizeBytes(), image.width(), image.height());
        } catch (IOException ignored) {
            if (targetCreated) {
                cleanupAfterFailure(target);
            }
            throw ContractError.of(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        }
    }

    public StoredImage load(String key) {
        Path target = resolve(key);
        if (!Files.isRegularFile(target)) {
            throw new ImageNotFoundException("Image file not found");
        }
        return new StoredImage(target, key.endsWith(".png") ? "image/png" : "image/jpeg");
    }

    /** Creates a physically independent file. Caller removes it only on known transaction rollback. */
    public String copy(String key) {
        StoredImage source = load(key);
        String copiedKey = UUID.randomUUID() + (key.endsWith(".png") ? ".png" : ".jpg");
        Path target = root.resolve(copiedKey);
        boolean targetCreated = false;
        try (OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            targetCreated = true;
            Files.copy(source.path(), output);
            return copiedKey;
        } catch (IOException ignored) {
            if (targetCreated) {
                cleanupAfterFailure(target);
            }
            throw new IllegalStateException("Image copy failed");
        }
    }

    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ignored) {
            throw new IllegalStateException("Image cleanup failed");
        }
    }

    private Path resolve(String key) {
        if (key == null || !key.matches("[0-9a-fA-F-]{36}\\.(jpg|png)")) {
            throw new ImageNotFoundException("Invalid image key");
        }
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new ImageNotFoundException("Invalid image path");
        }
        return target;
    }

    private void cleanupAfterFailure(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            log.error("Failed to clean up a partial image file");
        }
    }
}
