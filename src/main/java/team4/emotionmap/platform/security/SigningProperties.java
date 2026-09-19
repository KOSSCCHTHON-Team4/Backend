package team4.emotionmap.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.signing.*}: 분석 확인값·커서 서명 키. JWT 비밀과 별도 값이어야 한다.
 */
@ConfigurationProperties(prefix = "app.signing")
public record SigningProperties(String secret) {

    static final String LOCAL_DEVELOPMENT_SECRET =
            "local-dev-only-change-me-emotionmap-signing-secret-key-0987654321";
}
