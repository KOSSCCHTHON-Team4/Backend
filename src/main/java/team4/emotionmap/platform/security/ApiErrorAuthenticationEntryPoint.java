package team4.emotionmap.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.platform.web.ApiErrorWriter;

/**
 * 보호 자원의 401 을 {@code ApiError} JSON 으로 낸다. 코드는 필터가 남긴 사유대로
 * AUTH_REQUIRED / TOKEN_EXPIRED / INVALID_TOKEN 이고 {@code WWW-Authenticate: Bearer} 를 포함한다(API_SPEC 2.3).
 * 내부 경로·다른 사용자 정보는 넣지 않는다(A05 검증 시나리오).
 */
public class ApiErrorAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiErrorWriter writer;

    public ApiErrorAuthenticationEntryPoint(ApiErrorWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        writer.write(response, request, ContractError.of(AuthFailureReason.of(request).errorCode()));
    }
}
