package team4.emotionmap.media;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ImageStorageService {
    private final StorageProperties props;
    private final Path root;

    @Value("${app.storage.max-pixels:20000000}")
    private long maxPixels;

    public ImageStorageService(StorageProperties props) {
        this.props = props;
        this.root = Path.of(props.uploadDir()).toAbsolutePath().normalize();
    }

    public StoredUpload store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException("Image is empty");
        }
        if (file.getSize() > props.maxSizeBytes()) {
            throw new InvalidUploadException("Image exceeds maximum byte size");
        }
        try (InputStream stream = file.getInputStream();
             ImageInputStream input = ImageIO.createImageInputStream(stream)) {
            if (input == null) {
                throw new InvalidUploadException("Invalid image");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new InvalidUploadException("Invalid image format");
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("png")) {
                    throw new InvalidUploadException("Only JPEG and PNG images are supported");
                }
                if (!props.allowedExtensions().contains(format)
                        && !(format.equals("jpeg") && props.allowedExtensions().contains("jpg"))) {
                    throw new InvalidUploadException("Image format is disabled");
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > maxPixels) {
                    throw new InvalidUploadException("Image dimensions exceed the pixel limit");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw new InvalidUploadException("Invalid image data");
                }
                try {
                    Files.createDirectories(root);
                } catch (IOException e) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Image storage unavailable", e);
                }
                String key = UUID.randomUUID() + (format.equals("jpeg") ? ".jpg" : ".png");
                Path target = root.resolve(key);
                try {
                    if (!ImageIO.write(decoded, format, target.toFile())) {
                        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Image encoder unavailable");
                    }
                    long size = Files.size(target);
                    if (size > props.maxSizeBytes()) {
                        throw new InvalidUploadException("Encoded image exceeds maximum byte size");
                    }
                    return new StoredUpload(key, "image/" + format, size, width, height);
                } catch (IOException failure) {
                    cleanupAfterFailure(target, failure);
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Image storage unavailable", failure);
                } catch (RuntimeException failure) {
                    cleanupAfterFailure(target, failure);
                    throw failure;
                } finally {
                    decoded.flush();
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new InvalidUploadException("Image could not be decoded", e);
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
        try {
            Files.copy(source.path(), target);
            return copiedKey;
        } catch (IOException failure) {
            cleanupAfterFailure(target, failure);
            throw new IllegalStateException("Image copy failed", failure);
        }
    }

    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new IllegalStateException("Image cleanup failed", e);
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

    private void cleanupAfterFailure(Path target, Exception failure) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException cleanup) {
            failure.addSuppressed(cleanup);
        }
    }

    public record StoredUpload(String key, String mediaType, long sizeBytes, int width, int height) {
    }
}
