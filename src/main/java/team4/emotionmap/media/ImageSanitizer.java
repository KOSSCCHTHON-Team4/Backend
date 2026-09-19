package team4.emotionmap.media;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Component;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.media.ImageLimits;
import team4.emotionmap.contracts.media.ImageMediaType;
import team4.emotionmap.contracts.media.SanitizedImage;

/**
 * 업로드 바이트의 <b>실제</b> 검사·재인코딩(A06 순수 유틸, DB 무관). 확장자·클라이언트 MIME 은 보지 않는다.
 *
 * <p>순서(디컴프레션 폭탄 방지를 위해 전체 디코딩 전에 헤더로 크기를 먼저 본다):
 * <ol>
 *   <li>바이트 수 &gt; maxBytes → 413 IMAGE_TOO_LARGE</li>
 *   <li>매직 바이트가 JPEG/PNG 가 아님 → 415 UNSUPPORTED_IMAGE_TYPE</li>
 *   <li>헤더의 폭·높이·픽셀 수 초과 → 422 IMAGE_DIMENSIONS_EXCEEDED</li>
 *   <li>디코딩 실패·헤더와 실제 불일치·0 크기 → 422 INVALID_IMAGE</li>
 *   <li>같은 형식으로 재인코딩. BufferedImage 에서 다시 쓰므로 EXIF/XMP/ICC/텍스트 청크 등 메타데이터가 제거된다.</li>
 * </ol>
 * 한도는 {@code /config.limits} 에서 오는 {@link ImageLimits} 로 받는다(하드코딩 금지).
 */
@Component
public class ImageSanitizer {

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final float JPEG_QUALITY = 0.92f;

    static {
        ImageIO.setUseCache(false);
    }

    public SanitizedImage sanitize(byte[] original, ImageLimits limits) {
        if (original == null || original.length == 0) {
            throw ContractError.of(ErrorCode.INVALID_IMAGE);
        }
        if (original.length > limits.maxBytes()) {
            throw ContractError.of(ErrorCode.IMAGE_TOO_LARGE);
        }
        ImageMediaType type = sniff(original)
                .orElseThrow(() -> ContractError.of(ErrorCode.UNSUPPORTED_IMAGE_TYPE));

        BufferedImage decoded;
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            ImageReader reader = readerFor(type, in);
            try {
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                checkDimensions(width, height, limits);
                decoded = reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (ContractError e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw ContractError.withCause(ErrorCode.INVALID_IMAGE, e);
        }
        if (decoded == null || decoded.getWidth() < 1 || decoded.getHeight() < 1) {
            throw ContractError.of(ErrorCode.INVALID_IMAGE);
        }
        // 헤더와 실제 디코딩 크기가 다르면 조작된 파일로 본다.
        checkDimensions(decoded.getWidth(), decoded.getHeight(), limits);

        byte[] reencoded = reencode(decoded, type);
        if (reencoded.length > limits.maxBytes()) {
            throw ContractError.of(ErrorCode.IMAGE_TOO_LARGE);
        }
        return new SanitizedImage(reencoded, type, decoded.getWidth(), decoded.getHeight());
    }

    /** 매직 바이트로만 판정한다. */
    public static java.util.Optional<ImageMediaType> sniff(byte[] bytes) {
        if (startsWith(bytes, JPEG_MAGIC)) {
            return java.util.Optional.of(ImageMediaType.JPEG);
        }
        if (startsWith(bytes, PNG_MAGIC)) {
            return java.util.Optional.of(ImageMediaType.PNG);
        }
        return java.util.Optional.empty();
    }

    private static void checkDimensions(int width, int height, ImageLimits limits) {
        if (width < 1 || height < 1) {
            throw ContractError.of(ErrorCode.INVALID_IMAGE);
        }
        if (width > limits.maxWidth() || height > limits.maxHeight()
                || (long) width * (long) height > limits.maxPixels()) {
            throw ContractError.of(ErrorCode.IMAGE_DIMENSIONS_EXCEEDED);
        }
    }

    private static ImageReader readerFor(ImageMediaType type, ImageInputStream in) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByMIMEType(type.mimeType());
        if (!readers.hasNext()) {
            throw ContractError.of(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
        return readers.next();
    }

    private static byte[] reencode(BufferedImage source, ImageMediaType type) {
        BufferedImage canvas = normalize(source, type);
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType(type.mimeType());
        if (!writers.hasNext()) {
            throw ContractError.of(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (type == ImageMediaType.JPEG && param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
            // metadata 인자 null → 원본 메타데이터(EXIF/XMP/tEXt 등)를 옮기지 않는다.
            writer.write(null, new IIOImage(canvas, null, null), param);
        } catch (IOException | RuntimeException e) {
            throw ContractError.withCause(ErrorCode.INVALID_IMAGE, e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    /** JPEG 는 알파 없는 RGB 로, PNG 는 알파 보존 ARGB 로 고정 색공간에 다시 그린다(CMYK·인덱스 등 제거). */
    private static BufferedImage normalize(BufferedImage source, ImageMediaType type) {
        int imageType = type == ImageMediaType.PNG ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage canvas = new BufferedImage(source.getWidth(), source.getHeight(), imageType);
        Graphics2D g = canvas.createGraphics();
        try {
            if (imageType == BufferedImage.TYPE_INT_RGB) {
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
            }
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return canvas;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
