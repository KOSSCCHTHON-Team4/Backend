package team4.emotionmap.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * DB 비연결 단위테스트: JWT 발급 → 검증 왕복.
 * Security 배선 전체는 DB 부팅이 필요해 여기서 다루지 않고, 순수 로직만 검증한다.
 */
class JwtTokenProviderTest {

    private final JwtTokenProvider provider = new JwtTokenProvider(
            new JwtProperties("test-secret-test-secret-test-secret-test-secret-123456", 3_600_000L));

    @Test
    void createAndParseRoundTrip() {
        UUID userId = UUID.randomUUID();
        String token = provider.createToken(userId).token();
        assertThat(provider.parseUserId(token)).isEqualTo(userId);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = provider.createToken(UUID.randomUUID()).token();
        // 서명 세그먼트(마지막 . 뒤)를 훼손하면 검증에서 반드시 예외가 나야 한다.
        int lastDot = token.lastIndexOf('.');
        String tampered = token.substring(0, lastDot + 1) + "AAAA";
        assertThatThrownBy(() -> provider.parseUserId(tampered))
                .isInstanceOf(Exception.class);
    }

    @Test
    void garbageTokenIsRejected() {
        assertThatThrownBy(() -> provider.parseUserId("not-a-jwt"))
                .isInstanceOf(Exception.class);
    }
}
