package team4.emotionmap.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import team4.emotionmap.contracts.config.AuthConfig;
import team4.emotionmap.contracts.config.ServiceConfig;
import team4.emotionmap.contracts.config.ServiceConfigSource;
import team4.emotionmap.contracts.config.ServiceLimits;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.geo.GeoPoint;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-test-secret-123456";
    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");

    private static ServiceConfig config(int ttlSeconds) {
        return new ServiceConfig(
                "test",
                1_000,
                new GeoPoint(37, 127),
                new ServiceLimits(
                        3_000,
                        1_000,
                        1_000,
                        1,
                        1,
                        1,
                        1,
                        1,
                        1,
                        1,
                        1,
                        1,
                        1
                ),
                new AuthConfig(ttlSeconds, 5, 60)
        );
    }

    private static JwtTokenProvider provider(int ttlSeconds) {
        ServiceConfigSource source = () -> config(ttlSeconds);
        return new JwtTokenProvider(
                new JwtProperties(SECRET),
                source,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static SecretKey signingKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private static Claims verifiedClaims(String token) {
        return Jwts.parser()
                .clock(() -> Date.from(NOW))
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Test
    void createAndParseRoundTrip() {
        UUID userId = UUID.randomUUID();
        JwtTokenProvider p = provider(3_600);

        assertThat(p.parseUserId(p.createToken(userId).token())).isEqualTo(userId);
    }

    @Test
    void expirationIsExactlyConfiguredTtlFromIssuedAt() {
        int ttlSeconds = 37;
        JwtTokenProvider.IssuedToken issued = provider(ttlSeconds).createToken(UUID.randomUUID());
        Claims claims = verifiedClaims(issued.token());
        Instant issuedAt = claims.getIssuedAt().toInstant();
        Instant expiresAt = claims.getExpiration().toInstant();

        assertThat(Duration.between(issuedAt, expiresAt)).isEqualTo(Duration.ofSeconds(ttlSeconds));
        assertThat(issued.expiresInSeconds()).isEqualTo(ttlSeconds);
        assertThat(issued.expiresAt()).isEqualTo(expiresAt);
        assertThat(issuedAt).isEqualTo(NOW);
        assertThat(expiresAt).isEqualTo(NOW.plusSeconds(ttlSeconds));
    }

    @Test
    void fixedClockControlsExpiry() {
        String token = provider(1).createToken(UUID.randomUUID()).token();
        JwtTokenProvider later = new JwtTokenProvider(
                new JwtProperties(SECRET),
                () -> config(1),
                Clock.fixed(NOW.plusSeconds(2), ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> later.parseUserId(token))
                .isExactlyInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void unavailableServiceConfigPropagatesAsConfigurationUnavailable() {
        ServiceConfigSource unavailable = () -> {
            throw new ContractError(ErrorCode.CONFIGURATION_UNAVAILABLE);
        };
        JwtTokenProvider p = new JwtTokenProvider(
                new JwtProperties(SECRET),
                unavailable,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> p.createToken(UUID.randomUUID()))
                .isExactlyInstanceOf(ContractError.class)
                .satisfies(throwable -> {
                    ContractError error = (ContractError) throwable;
                    assertThat(error.code()).isEqualTo(ErrorCode.CONFIGURATION_UNAVAILABLE);
                    assertThat(error.httpStatus()).isEqualTo(503);
                });
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtTokenProvider p = provider(3_600);
        String token = p.createToken(UUID.randomUUID()).token();
        int lastDot = token.lastIndexOf('.');

        assertThatThrownBy(() -> p.parseUserId(token.substring(0, lastDot + 1) + "AAAA"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void garbageTokenIsRejected() {
        assertThatThrownBy(() -> provider(3_600).parseUserId("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }
}
