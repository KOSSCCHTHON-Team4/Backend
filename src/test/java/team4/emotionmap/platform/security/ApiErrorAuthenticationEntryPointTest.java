package team4.emotionmap.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import team4.emotionmap.platform.web.ApiErrorWriter;
import team4.emotionmap.platform.web.json.StrictJson;

/** UUID authentication and current account access share the ApiError response contract. */
class ApiErrorAuthenticationEntryPointTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-test-secret-123456";
    private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000042");
    private final JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties(SECRET, 3_600_000L));
    private final ApiErrorWriter writer = new ApiErrorWriter(StrictJson.mapper());
    private final ApiErrorAuthenticationEntryPoint entryPoint = new ApiErrorAuthenticationEntryPoint(writer);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request(String method, String path, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    private MockHttpServletResponse run(String authorization) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = request("GET", "/v1/config", authorization);
        new JwtAuthenticationFilter(provider, (userId, path) -> fail("Invalid JWT must not reach account access"), writer)
                .doFilter(request, response, (req, res) -> entryPoint.commence(request, response,
                        new InsufficientAuthenticationException("none")));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
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
        String expiredToken = Jwts.builder().subject(USER_ID.toString())
                .issuedAt(Date.from(Instant.EPOCH)).expiration(Date.from(Instant.EPOCH.plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        MockHttpServletResponse r = run("Bearer " + expiredToken);
        assertThat(r.getStatus()).isEqualTo(401);
        assertThat(r.getContentAsString()).contains("\"code\":\"TOKEN_EXPIRED\"");
        assertThat(r.getHeader("WWW-Authenticate"))
                .isEqualTo("Bearer error=\"invalid_token\", error_description=\"expired\"");
    }

    @Test
    void garbageTokenIsInvalidToken() throws Exception {
        MockHttpServletResponse r = run("Bearer not.a.jwt");
        assertThat(r.getStatus()).isEqualTo(401);
        assertThat(r.getContentAsString()).contains("\"code\":\"INVALID_TOKEN\"").doesNotContain("not.a.jwt");
        assertThat(r.getHeader("WWW-Authenticate")).isEqualTo("Bearer error=\"invalid_token\"");
    }

    @Test
    void signedTokenWithoutSubjectIsInvalidToken() throws Exception {
        String token = Jwts.builder().issuedAt(Date.from(Instant.EPOCH))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        MockHttpServletResponse r = run("Bearer " + token);
        assertThat(r.getStatus()).isEqualTo(401);
        assertThat(r.getContentAsString()).contains("\"code\":\"INVALID_TOKEN\"");
    }

    @Test
    void validTokenAuthenticatesWithCurrentAccountRole() throws Exception {
        MockHttpServletRequest request = request("GET", "/v1/config", "Bearer " + provider.createToken(USER_ID).token());
        MockHttpServletResponse response = new MockHttpServletResponse();
        new JwtAuthenticationFilter(provider, (userId, path) -> "ROLE_ADMIN", writer)
                .doFilter(request, response, (req, res) -> {
                    var authentication = SecurityContextHolder.getContext().getAuthentication();
                    assertThat(authentication.getPrincipal()).isEqualTo(USER_ID);
                    assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
                    res.getWriter().write("authenticated");
                });
        assertThat(response.getContentAsString()).isEqualTo("authenticated");
    }

    @Test
    void onboardingDenialStopsRequestAndUsesStructuredError() throws Exception {
        MockHttpServletRequest request = request("GET", "/v1/memories", "Bearer " + provider.createToken(USER_ID).token());
        MockHttpServletResponse response = new MockHttpServletResponse();
        AccountAccessGuard guard = (userId, path) -> {
            assertThat(userId).isEqualTo(USER_ID);
            assertThat(path).isEqualTo("/v1/memories");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ONBOARDING_REQUIRED");
        };
        new JwtAuthenticationFilter(provider, guard, writer).doFilter(request, response,
                (req, res) -> fail("Denied accounts must not reach the protected resource"));
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":\"ONBOARDING_REQUIRED\"");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("private, no-store");
        assertThat(StrictJson.mapper().readTree(response.getContentAsString()).get("requestId").asString())
                .isEqualTo(response.getHeader("X-Request-Id"));
        assertThat(response.getHeader("WWW-Authenticate")).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void deletedAccountReturnsInvalidTokenRatherThanGenericForbidden() throws Exception {
        MockHttpServletRequest request = request("GET", "/v1/config", "Bearer " + provider.createToken(USER_ID).token());
        MockHttpServletResponse response = new MockHttpServletResponse();
        new JwtAuthenticationFilter(provider, (userId, path) -> {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN");
        }, writer).doFilter(request, response, (req, res) -> fail("Deleted account must be denied"));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"INVALID_TOKEN\"");
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer error=\"invalid_token\"");
    }

    @Test
    void loginIgnoresStaleBearerTokenAndAccountGuard() throws Exception {
        MockHttpServletRequest request = request("POST", "/v1/auth/login", "Bearer stale-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new JwtAuthenticationFilter(provider, (userId, path) -> fail("Public login must bypass account access"), writer)
                .doFilter(request, response, (req, res) -> res.getWriter().write("login"));
        assertThat(response.getContentAsString()).isEqualTo("login");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void userContextAcceptsOnlyUuidPrincipals() {
        SecurityContextUserContext ctx = new SecurityContextUserContext();
        assertThat(ctx.currentUserId()).isEmpty();
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.TestingAuthenticationToken(USER_ID, null, "ROLE_USER"));
        assertThat(ctx.currentUserId()).contains(USER_ID);
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.TestingAuthenticationToken(42L, null, "ROLE_USER"));
        assertThat(ctx.currentUserId()).isEmpty();
    }
}
