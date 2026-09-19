package team4.emotionmap.platform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationSeconds;

    public JwtTokenProvider(JwtProperties props) {
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = props.expirationMillis() / 1000;
        if (expirationSeconds < 1) {
            throw new IllegalArgumentException("JWT expiration must be at least one second");
        }
    }

    public IssuedToken createToken(UUID userId) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiry = now.plusSeconds(expirationSeconds);
        String token = Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiry, expirationSeconds);
    }

    public UUID parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (claims.getSubject() == null) {
            throw new JwtException("JWT subject is required");
        }
        return UUID.fromString(claims.getSubject());
    }

    public record IssuedToken(String token, Instant expiresAt, long expiresInSeconds) { }
}
