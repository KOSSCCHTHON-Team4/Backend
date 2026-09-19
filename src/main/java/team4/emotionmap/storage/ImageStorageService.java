package team4.emotionmap.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import team4.emotionmap.config.StorageProperties;

/**
 * 이미지 로컬 저장/조회 서비스.
 *
 * 동작:
 *   - store(file): 업로드 검증(빈 파일/확장자/크기) 후 UUID 키({uuid}.{ext})로 uploadDir 에 저장,
 *                  DB(memory.image_path)에 넣을 key 를 반환한다.
 *   - load(key)  : key 를 검증하고 실제 파일을 StoredImage(경로 + MIME)로 반환한다.
 *
 * 보안:
 *   - 원본 파일명을 쓰지 않고 UUID 키로 저장한다.
 *   - load 시 key 에 경로 구분자/상위경로(.. / /)가 있으면 거부하고, 최종 경로가
 *     uploadDir 밖으로 나가지 않는지(normalize 후 startsWith) 확인한다 (path traversal 방어).
 *
 * NOTE: 공통 베이스 골격이다. 실제 예외 → HTTP 매핑, 이미지 리사이징 등은 기능 개발 단계에서.
 */
@Slf4j
@Service
public class ImageStorageService {

    private final StorageProperties props;
    private final Path root;

    public ImageStorageService(StorageProperties props) {
        this.props = props;
        this.root = Path.of(props.uploadDir()).toAbsolutePath().normalize();
    }

    /** 저장 디렉토리를 준비한다(없으면 생성). 빈 등록 후 최초 사용 전 호출되도록 서비스에서 보장. */
    private void ensureRoot() {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("이미지 저장 디렉토리를 만들 수 없습니다: " + root, e);
        }
    }

    /**
     * 업로드 파일을 저장하고 key 를 반환한다.
     * @return DB 에 저장할 이미지 key (예: "3f2b...e1.jpg")
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException("빈 파일은 업로드할 수 없습니다.");
        }
        if (file.getSize() > props.maxSizeBytes()) {
            throw new InvalidUploadException("파일이 허용 크기를 초과했습니다: " + file.getSize());
        }
        String ext = extractExtension(file.getOriginalFilename());
        if (!props.allowedExtensions().contains(ext)) {
            throw new InvalidUploadException("허용되지 않는 확장자입니다: " + ext);
        }

        ensureRoot();
        String key = UUID.randomUUID() + "." + ext;
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {   // 방어적: 정상 흐름에선 발생하지 않음
            throw new InvalidUploadException("잘못된 저장 경로입니다.");
        }
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("이미지 저장에 실패했습니다: " + key, e);
        }
        log.info("image stored: key={}, size={}", key, file.getSize());
        return key;
    }

    /**
     * key 에 해당하는 이미지를 로드한다.
     * @throws ImageNotFoundException key 형식이 잘못됐거나 파일이 없을 때
     */
    public StoredImage load(String key) {
        if (key == null || key.isBlank()
                || key.contains("..") || key.contains("/") || key.contains("\\")) {
            throw new ImageNotFoundException("잘못된 이미지 key: " + key);
        }
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) {
            throw new ImageNotFoundException("이미지를 찾을 수 없습니다: " + key);
        }
        return new StoredImage(target, contentTypeOf(key));
    }

    /** 파일명에서 소문자 확장자를 뽑는다. 없으면 InvalidUploadException. */
    private String extractExtension(String filename) {
        if (filename == null) {
            throw new InvalidUploadException("파일명이 없습니다.");
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            throw new InvalidUploadException("확장자가 없는 파일입니다: " + filename);
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 확장자로 MIME 을 추론한다(6번 결정: 별도 컬럼 없이 확장자 기반). */
    private String contentTypeOf(String key) {
        String ext = key.substring(key.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }
}
