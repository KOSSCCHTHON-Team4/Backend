package team4.emotionmap.media.dto;

/**
 * 이미지 업로드 응답.
 *   key : DB(memory.image_path)에 저장할 이미지 키
 *   url : 클라이언트가 이미지를 조회할 경로 (바이너리 조회 API)
 */
public record ImageUploadResponse(String key, String url) {

    public static ImageUploadResponse of(String key) {
        return new ImageUploadResponse(key, "/api/images/" + key);
    }
}
