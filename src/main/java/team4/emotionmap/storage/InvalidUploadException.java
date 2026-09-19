package team4.emotionmap.storage;

/** 업로드 검증 실패(확장자/크기/빈 파일 등). 컨트롤러에서 400 으로 매핑 권장. */
public class InvalidUploadException extends RuntimeException {
    public InvalidUploadException(String message) {
        super(message);
    }
}
