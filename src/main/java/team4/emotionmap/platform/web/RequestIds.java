package team4.emotionmap.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

/** 요청 단위 requestId. 서버가 생성하며 클라이언트 값은 신뢰하지 않는다. 로그 MDC 와 ApiError.requestId 에 같은 값. */
public final class RequestIds {

    public static final String ATTRIBUTE = RequestIds.class.getName() + ".requestId";
    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private RequestIds() {
    }

    public static String current(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        if (value instanceof String s) {
            return s;
        }
        String created = UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, created);
        return created;
    }
}
