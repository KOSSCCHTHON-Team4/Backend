package team4.emotionmap.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.jwt.* 설정 바인딩.
 *
 * @param secret          HMAC 서명 키 (Base64 또는 충분히 긴 문자열). 환경변수로만 주입, 커밋 금지.
 * @param expirationMillis 액세스 토큰 만료(ms)
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        long expirationMillis
) {
}
