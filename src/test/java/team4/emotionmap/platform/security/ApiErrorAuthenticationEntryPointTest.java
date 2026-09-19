package team4.emotionmap.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import team4.emotionmap.platform.web.ApiErrorWriter;
import team4.emotionmap.platform.web.json.StrictJson;

/** A05: 토큰 없음/만료/위조를 401 ApiError 로, WWW-Authenticate 와 함께 낸다. 내부 정보 노출 없음. */
class ApiErrorAuthenticationEntryPointTest {

    private final JwtTokenProvider provider = new JwtTokenProvider(
            new JwtProperties("test-secret-test-secret-test-secret-test-secret-123456", 3_600_000L));
    private final ApiErrorAuthenticationEntryPoint entryPoint =
            new ApiErrorAuthenticationEntryPoint(new ApiErrorWriter(StrictJson.mapper()));

    private MockHttpServletResponse run(String authorization) throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/config");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        new JwtAuthenticationFilter(provider).doFilter(request, response, new MockFilterChain());
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            entryPoint.commence(request, response, new InsufficientAuthenticationException("none"));
        }
        SecurityContextHolder.clearContext();
        return response;
    }

    @Test
    void missingTokenIsAuthRequired() throws Exception {
        MockHttpServletResponse r = run(null);
        assertThat(r.getStatus()).isEqualTo(401);
        assertThat(r.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(r.getHeader("Cache-Control")).isEqualTo("private, no-store");
        assertThat(r.getContentAsString()).contains("\"code\":\"AUTH_REQUIRED\"").contains("\"requestId\"");
    }

    @Test
    void expiredTokenIsTokenExpired() throws Exception {
        JwtTokenProvider expiredIssuer = new JwtTokenProvider(
                new JwtProperties("test-secret-test-secret-test-secret-test-secret-123456", -1_000L));
        MockHttpServletResponse r = run("Bearer " + expiredIssuer.createToken(7L));
        assertThat(r.getStatus()).isEqualTo(401);
        assertThat(r.getContentAsString()).contains("\"code\":\"TOKEN_EXPIRED\"");
        assertThat(r.getHeader("WWW-Authenticate")).contains("invalid_token");
    }

    @Test
    void garbageTokenIsInvalidToken() throws Exception {
        MockHttpServletResponse r = run("Bearer not.a.jwt");
        assertThat(r.getStatus()).isEqualTo(401);
        assertThat(r.getContentAsString()).contains("\"code\":\"INVALID_TOKEN\"").doesNotContain("not.a.jwt");
    }

    @Test
    void validTokenAuthenticates() throws Exception {
        MockHttpServletResponse r = run("Bearer " + provider.createToken(42L));
        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(r.getContentAsString()).isEmpty();
    }

    @Test
    void userContextAcceptsOnlyUuidPrincipals() {
        SecurityContextUserContext ctx = new SecurityContextUserContext();
        assertThat(ctx.currentUserId()).isEmpty();
        UUID id = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.TestingAuthenticationToken(id, null, "ROLE_USER"));
        assertThat(ctx.currentUserId()).contains(id);
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.TestingAuthenticationToken(42L, null, "ROLE_USER"));
        assertThat(ctx.currentUserId()).isEmpty();     // legacy Long principal 은 인증으로 보지 않음
        SecurityContextHolder.clearContext();
    }
}
