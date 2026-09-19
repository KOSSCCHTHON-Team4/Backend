package team4.emotionmap.platform.security;

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

/** Validates UUID JWT subjects and rechecks current account access on every request. */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final AccountAccessGuard accountAccessGuard;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, AccountAccessGuard accountAccessGuard) {
        this.tokenProvider = tokenProvider;
        this.accountAccessGuard = accountAccessGuard;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return (request.getMethod().equals("POST") && path.equals("/v1/auth/login"))
                || path.equals("/swagger-ui.html") || path.startsWith("/swagger-ui/")
                || path.equals("/v3/api-docs") || path.startsWith("/v3/api-docs/")
                || path.equals("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            UUID userId;
            try {
                userId = tokenProvider.parseUserId(header.substring(BEARER_PREFIX.length()));
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "INVALID_TOKEN");
                return;
            }
            String role;
            try {
                role = accountAccessGuard.requireAccess(userId, request.getServletPath());
            } catch (ResponseStatusException e) {
                SecurityContextHolder.clearContext();
                response.sendError(e.getStatusCode().value(), e.getReason());
                return;
            }
            var authentication = new UsernamePasswordAuthenticationToken(
                    userId, null, List.of(new SimpleGrantedAuthority(role)));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }
}
