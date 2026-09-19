package team4.emotionmap.platform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;
import team4.emotionmap.contracts.config.ServiceConfigSource;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final ServiceConfigSource serviceConfig;
    private final Clock clock;

    public JwtTokenProvider(JwtProperties properties, ServiceConfigSource serviceConfig, Clock clock) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.serviceConfig = serviceConfig;
        this.clock = clock;
    }

    public IssuedToken createToken(UUID userId) {
        long expirationSeconds = serviceConfig.current().auth().accessTokenTtlSeconds();
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
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
                .clock(() -> Date.from(clock.instant()))
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
