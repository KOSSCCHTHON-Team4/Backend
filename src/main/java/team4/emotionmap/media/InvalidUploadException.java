package team4.emotionmap.media;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidUploadException extends RuntimeException {
    public InvalidUploadException(String message) {
        super(message);
    }

    public InvalidUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
