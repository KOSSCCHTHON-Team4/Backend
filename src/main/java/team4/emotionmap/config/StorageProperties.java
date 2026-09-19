package team4.emotionmap.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.storage.* 설정 바인딩.
 *
 * 이미지는 로컬 파일시스템의 uploadDir 아래에 UUID 키(예: {uuid}.jpg)로 저장하고,
 * DB(memory.image_path)에는 그 key 만 기록한다. 실제 경로는 서버가 uploadDir + key 로 조합한다.
 * (원본 경로/파일명을 노출하지 않아 path traversal 위험을 줄인다.)
 *
 * @param uploadDir         이미지가 저장될 로컬 루트 디렉토리 (프로필별로 다름)
 * @param allowedExtensions 허용 확장자 목록 (소문자, 점 없이. 예: jpg, png, webp)
 * @param maxSizeBytes      업로드 허용 최대 크기(바이트). 초과 시 거부.
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String uploadDir,
        List<String> allowedExtensions,
        long maxSizeBytes
) {
}
