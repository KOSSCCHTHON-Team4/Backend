package team4.emotionmap.media;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import team4.emotionmap.media.dto.ImageUploadResponse;

/**
 * 이미지 API (JSON 업로드 응답 / 바이너리 조회 응답 분리).
 *
 *   POST /api/images        (multipart)  -> { "key": "...", "url": "/api/images/..." }  [JSON]
 *   GET  /api/images/{key}                -> 이미지 바이너리                              [BINARY]
 *
 * 게시글(Memory) 조회는 JSON 으로 image key/URL 만 담고, 실제 바이너리는 위 GET 으로 따로 받는다.
 * 이렇게 응답 타입을 분리하면 클라이언트가 일관된 타입으로 처리할 수 있다.
 *
 * NOTE: 공통 베이스 골격. 예외 → HTTP 상태 매핑(@ControllerAdvice)은 기능 개발 단계에서 추가.
 */
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageStorageService imageStorageService;

    /** 이미지 업로드: multipart 로 받고, 저장 후 key/url 을 JSON 으로 반환. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImageUploadResponse upload(@RequestParam("file") MultipartFile file) {
        String key = imageStorageService.store(file);
        return ImageUploadResponse.of(key);
    }

    /** 이미지 조회: key 에 해당하는 파일을 바이너리로 스트리밍. */
    @GetMapping("/{key}")
    public ResponseEntity<Resource> serve(@PathVariable String key) {
        StoredImage image = imageStorageService.load(key);
        Resource body = new FileSystemResource(image.path());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, image.contentType())
                .body(body);
    }
}
