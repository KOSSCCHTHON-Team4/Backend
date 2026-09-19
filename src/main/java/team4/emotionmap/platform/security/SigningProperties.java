package team4.emotionmap.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.signing.*}: 분석 확인값·커서 서명 키. JWT 비밀과 <b>별도</b> 값이어야 한다(용도 분리).
 * 운영에서는 {@code SIGNING_SECRET} 환경변수로만 주입하고 커밋하지 않는다.
 *
 * @param secret HMAC 키 원문. 32바이트 이상 권장
 */
@ConfigurationProperties(prefix = "app.signing")
public record SigningProperties(String secret) {
}
