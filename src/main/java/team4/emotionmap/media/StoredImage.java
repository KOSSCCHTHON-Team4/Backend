package team4.emotionmap.media;

import java.nio.file.Path;

/**
 * 로드된 이미지 한 건. 컨트롤러가 바이너리 응답을 구성할 때 사용.
 *
 * @param path        실제 파일 경로 (Resource 로 감싸 스트리밍)
 * @param contentType 확장자로 추론한 MIME (예: image/jpeg). 알 수 없으면 application/octet-stream
 */
public record StoredImage(Path path, String contentType) {
}
