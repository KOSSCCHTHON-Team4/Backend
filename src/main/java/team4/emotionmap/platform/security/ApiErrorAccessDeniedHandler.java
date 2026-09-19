package team4.emotionmap.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.platform.web.ApiErrorWriter;

/**
 * 인증은 됐지만 권한 규칙에 막힌 경우. 명세는 "권한 없는 대상 = 404 로 존재 여부 비노출"이므로
 * 자원 단위 인가 실패는 서비스가 ContractError(404/410)로 던지는 것이 원칙이고, 여기는 최후 방어선이다.
 */
public class ApiErrorAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiErrorWriter writer;

    public ApiErrorAccessDeniedHandler(ApiErrorWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        writer.write(response, request, ContractError.of(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
