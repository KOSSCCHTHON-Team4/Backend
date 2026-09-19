package team4.emotionmap.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.jwt.*} 설정 바인딩.
 *
 * @param secret HMAC 서명 키. 만료 시간은 {@code ServiceConfigSource}의 auth 설정만 사용한다.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret) {

    static final String LOCAL_DEVELOPMENT_SECRET =
            "local-dev-only-change-me-emotionmap-jwt-secret-key-1234567890";
}
