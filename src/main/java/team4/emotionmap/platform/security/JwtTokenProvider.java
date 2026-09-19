package team4.emotionmap.platform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * JWT 발급/검증 유틸.
 *   - subject 에 userId 를 담는다.
 *   - HMAC-SHA 서명(secret 기반).
 * jjwt 0.13.x API 사용.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationMillis;

    public JwtTokenProvider(JwtProperties props) {
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = props.expirationMillis();
    }

    /** userId 로 액세스 토큰 발급. */
    public String createToken(Long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMillis);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * 토큰을 검증하고 userId 를 반환한다.
     * @throws JwtException 유효하지 않은 토큰
     */
    public Long parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.valueOf(claims.getSubject());
    }
}
