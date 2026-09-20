package team4.emotionmap.platform.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.platform.web.ApiErrorWriter;

/**
 * Validates UUID JWT subjects and rechecks current account access on every request.
 * Authentication failures retain their expired/invalid distinction for the ApiError entry point.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final AccountAccessGuard accountAccessGuard;
    private final ApiErrorWriter apiErrorWriter;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, AccountAccessGuard accountAccessGuard,
                                   ApiErrorWriter apiErrorWriter) {
        this.tokenProvider = tokenProvider;
        this.accountAccessGuard = accountAccessGuard;
        this.apiErrorWriter = apiErrorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return (request.getMethod().equals("POST") && path.equals("/v1/auth/login"))
                || path.equals("/swagger-ui.html") || path.startsWith("/swagger-ui/")
                || path.equals("/v3/api-docs") || path.startsWith("/v3/api-docs/")
                || path.equals("/v3/api-docs.yaml")
                || path.equals("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            AuthFailureReason.record(request, AuthFailureReason.MISSING);
            filterChain.doFilter(request, response);
            return;
        }
        UUID userId;
        try {
            userId = tokenProvider.parseUserId(header.substring(BEARER_PREFIX.length()).trim());
        } catch (ExpiredJwtException e) {
            SecurityContextHolder.clearContext();
            AuthFailureReason.record(request, AuthFailureReason.EXPIRED);
            filterChain.doFilter(request, response);
            return;
        } catch (JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            AuthFailureReason.record(request, AuthFailureReason.INVALID);
            filterChain.doFilter(request, response);
            return;
        }
        String role;
        try {
            role = accountAccessGuard.requireAccess(userId, request.getServletPath());
        } catch (ResponseStatusException e) {
            SecurityContextHolder.clearContext();
            apiErrorWriter.write(response, request, ApiErrorWriter.fromResponseStatus(e));
            return;
        } catch (ContractError e) {
            SecurityContextHolder.clearContext();
            apiErrorWriter.write(response, request, e);
            return;
        }
        var authentication = new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority(role)));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }
}
