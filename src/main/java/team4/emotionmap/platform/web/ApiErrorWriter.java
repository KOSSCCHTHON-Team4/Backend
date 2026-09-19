package team4.emotionmap.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import team4.emotionmap.contracts.error.ApiError;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link ApiError} 를 HTTP 응답으로 만드는 유일한 지점. 헤더 규칙(API_SPEC 2.1·2.3):
 * {@code Cache-Control: private, no-store}, 401 이면 {@code WWW-Authenticate: Bearer},
 * retryAfterSeconds 가 있으면 같은 값의 {@code Retry-After}.
 */
@Component
public class ApiErrorWriter {

    public static final String CACHE_CONTROL_VALUE = "private, no-store";

    private final JsonMapper mapper;

    public ApiErrorWriter(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public ResponseEntity<ApiError> toResponse(ContractError error, HttpServletRequest request) {
        ApiError body = ApiError.of(error, RequestIds.current(request));
        return ResponseEntity.status(error.httpStatus()).headers(headersFor(body)).body(body);
    }

    public ResponseEntity<ApiError> toResponse(ErrorCode code, HttpServletRequest request) {
        return toResponse(ContractError.of(code), request);
    }

    /** 보안 필터(EntryPoint/AccessDeniedHandler)처럼 MVC 밖에서 직접 쓸 때. */
    public void write(HttpServletResponse response, HttpServletRequest request, ContractError error) throws IOException {
        ApiError body = ApiError.of(error, RequestIds.current(request));
        response.setStatus(error.httpStatus());
        headersFor(body).forEach((name, values) -> values.forEach(v -> response.addHeader(name, v)));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(mapper.writeValueAsString(body));
        response.getWriter().flush();
    }

    public static HttpHeaders headersFor(ApiError body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_VALUE);
        headers.set(RequestIds.HEADER, body.requestId());
        if (body.code().isUnauthorized()) {
            headers.set(HttpHeaders.WWW_AUTHENTICATE, wwwAuthenticate(body.code()));
        }
        if (body.retryAfterSeconds() != null) {
            headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(body.retryAfterSeconds()));
        }
        return headers;
    }

    private static String wwwAuthenticate(ErrorCode code) {
        return switch (code) {
            case TOKEN_EXPIRED -> "Bearer error=\"invalid_token\", error_description=\"expired\"";
            case INVALID_TOKEN -> "Bearer error=\"invalid_token\"";
            default -> "Bearer";
        };
    }
}
