package team4.emotionmap.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.media.ImageLimits;
import team4.emotionmap.contracts.media.ImageMediaType;
import team4.emotionmap.contracts.media.SanitizedImage;

/** A16: 가짜 확장자·과대 파일·픽셀 초과를 실제 바이트로 검증하고, 재인코딩으로 메타데이터가 사라지는지 확인한다. */
class ImageSanitizerTest {

    private final ImageSanitizer sanitizer = new ImageSanitizer();
    private final ImageLimits limits = new ImageLimits(5_242_880L, 6000, 6000, 20_000_000L);

    private static byte[] image(String format, int w, int h, boolean alpha) throws Exception {
        BufferedImage img = new BufferedImage(w, h, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(0, 0, 255, alpha ? 128 : 255));
        g.fillOval(0, 0, w / 2, h / 2);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(ImageIO.write(img, format, out)).isTrue();
        return out.toByteArray();
    }

    @Test
    void jpegAndPngAreAcceptedAndReencoded() throws Exception {
        SanitizedImage jpeg = sanitizer.sanitize(image("jpg", 64, 48, false), limits);
        assertThat(jpeg.mediaType()).isEqualTo(ImageMediaType.JPEG);
        assertThat(jpeg.width()).isEqualTo(64);
        assertThat(jpeg.height()).isEqualTo(48);
        assertThat(ImageSanitizer.sniff(jpeg.bytes())).contains(ImageMediaType.JPEG);

        SanitizedImage png = sanitizer.sanitize(image("png", 32, 32, true), limits);
        assertThat(png.mediaType()).isEqualTo(ImageMediaType.PNG);
        assertThat(ImageSanitizer.sniff(png.bytes())).contains(ImageMediaType.PNG);
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(png.bytes())).getColorModel().hasAlpha()).isTrue();
    }

    @Test
    void contentTypeIsDecidedByBytesNotByName() throws Exception {
        // "photo.jpg" 라고 올라와도 바이트가 PNG 면 PNG 로 판정한다(확장자는 아예 보지 않는다).
        assertThat(sanitizer.sanitize(image("png", 8, 8, false), limits).mediaType()).isEqualTo(ImageMediaType.PNG);
        byte[] fakeJpeg = "GIF89a....not really an image".getBytes(StandardCharsets.US_ASCII);
        assertThat(catchThrowableOfType(ContractError.class, () -> sanitizer.sanitize(fakeJpeg, limits)).code())
                .isEqualTo(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        byte[] webp = new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        assertThat(catchThrowableOfType(ContractError.class, () -> sanitizer.sanitize(webp, limits)).code())
                .isEqualTo(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }

    @Test
    void truncatedFileIsInvalidImage() throws Exception {
        byte[] jpeg = image("jpg", 64, 64, false);
        byte[] cut = Arrays.copyOf(jpeg, jpeg.length / 3);
        assertThat(catchThrowableOfType(ContractError.class, () -> sanitizer.sanitize(cut, limits)).code())
                .isEqualTo(ErrorCode.INVALID_IMAGE);
        assertThat(catchThrowableOfType(ContractError.class, () -> sanitizer.sanitize(new byte[0], limits)).code())
                .isEqualTo(ErrorCode.INVALID_IMAGE);
    }

    @Test
    void byteAndDimensionLimitsComeFromConfig() throws Exception {
        byte[] png = image("png", 200, 100, false);
        assertThat(catchThrowableOfType(ContractError.class,
                () -> sanitizer.sanitize(png, new ImageLimits(png.length - 1, 6000, 6000, 20_000_000L))).code())
                .isEqualTo(ErrorCode.IMAGE_TOO_LARGE);
        assertThat(catchThrowableOfType(ContractError.class,
                () -> sanitizer.sanitize(png, new ImageLimits(5_242_880L, 150, 6000, 20_000_000L))).code())
                .isEqualTo(ErrorCode.IMAGE_DIMENSIONS_EXCEEDED);
        assertThat(catchThrowableOfType(ContractError.class,
                () -> sanitizer.sanitize(png, new ImageLimits(5_242_880L, 6000, 6000, 10_000L))).code())
                .isEqualTo(ErrorCode.IMAGE_DIMENSIONS_EXCEEDED);
        assertThat(sanitizer.sanitize(png, new ImageLimits(5_242_880L, 200, 100, 20_000L)).width()).isEqualTo(200);
    }

    @Test
    void exifSegmentIsStrippedFromJpeg() throws Exception {
        byte[] jpeg = image("jpg", 16, 16, false);
        byte[] exifPayload = "Exif\0\0SYNTHETIC-GPS-METADATA".getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);                       // SOI
        out.write(0xFF); out.write(0xE1);            // APP1
        int len = exifPayload.length + 2;
        out.write((len >> 8) & 0xFF); out.write(len & 0xFF);
        out.write(exifPayload);
        out.write(jpeg, 2, jpeg.length - 2);
        byte[] withExif = out.toByteArray();
        assertThat(new String(withExif, StandardCharsets.ISO_8859_1)).contains("SYNTHETIC-GPS-METADATA");

        SanitizedImage clean = sanitizer.sanitize(withExif, limits);
        assertThat(new String(clean.bytes(), StandardCharsets.ISO_8859_1))
                .doesNotContain("SYNTHETIC-GPS-METADATA").doesNotContain("Exif");
    }

    @Test
    void textChunkIsStrippedFromPng() throws Exception {
        byte[] png = image("png", 16, 16, false);
        // IHDR 직후에 tEXt 청크 삽입 (8 sig + 4 len + 4 type + 13 data + 4 crc = 33)
        byte[] keyword = "Comment\0SYNTHETIC-LOCATION".getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream chunk = new ByteArrayOutputStream();
        chunk.write(new byte[]{0, 0, 0, (byte) keyword.length});
        byte[] typeAndData = new byte[4 + keyword.length];
        System.arraycopy("tEXt".getBytes(StandardCharsets.US_ASCII), 0, typeAndData, 0, 4);
        System.arraycopy(keyword, 0, typeAndData, 4, keyword.length);
        chunk.write(typeAndData);
        CRC32 crc = new CRC32();
        crc.update(typeAndData);
        long v = crc.getValue();
        chunk.write(new byte[]{(byte) (v >> 24), (byte) (v >> 16), (byte) (v >> 8), (byte) v});
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(png, 0, 33);
        out.write(chunk.toByteArray());
        out.write(png, 33, png.length - 33);
        byte[] withText = out.toByteArray();
        assertThat(new String(withText, StandardCharsets.ISO_8859_1)).contains("SYNTHETIC-LOCATION");

        SanitizedImage clean = sanitizer.sanitize(withText, limits);
        assertThat(new String(clean.bytes(), StandardCharsets.ISO_8859_1)).doesNotContain("SYNTHETIC-LOCATION");
    }
}
