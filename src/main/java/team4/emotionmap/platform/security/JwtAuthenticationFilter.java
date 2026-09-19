package team4.emotionmap.platform.security;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bearer 토큰을 검증해 SecurityContext 에 인증을 설정하는 필터(C02 일부).
 * <ul>
 *   <li>principal 은 현재 {@code Long userId}(baseline). A01 에서 UUID 로 교체된다 — 컨트롤러는
 *       {@code contracts.account.UserContext} 를 통해 읽어 교체 영향을 격리한다.</li>
 *   <li>토큰이 없거나 유효하지 않으면 인증을 설정하지 않고 실패 사유만 요청 속성에 남긴다.
 *       401 응답 본문은 {@link ApiErrorAuthenticationEntryPoint} 가 만든다(TOKEN_EXPIRED/INVALID_TOKEN 구분).</li>
 *   <li>토큰 값은 로그에 남기지 않는다.</li>
 * </ul>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            AuthFailureReason.record(request, AuthFailureReason.MISSING);
        } else {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            try {
                Long userId = tokenProvider.parseUserId(token);
                var authentication = new UsernamePasswordAuthenticationToken(
                        userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (ExpiredJwtException e) {
                SecurityContextHolder.clearContext();
                AuthFailureReason.record(request, AuthFailureReason.EXPIRED);
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
                AuthFailureReason.record(request, AuthFailureReason.INVALID);
            }
        }
        filterChain.doFilter(request, response);
    }
}
