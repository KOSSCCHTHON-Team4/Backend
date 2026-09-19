package team4.emotionmap.storage;

/** 요청한 이미지 key 가 없거나 파일이 존재하지 않음. 컨트롤러에서 404 로 매핑 권장. */
public class ImageNotFoundException extends RuntimeException {
    public ImageNotFoundException(String message) {
        super(message);
    }
}
