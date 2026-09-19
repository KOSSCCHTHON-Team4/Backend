package team4.emotionmap.contracts.config;

/**
 * {@code /config.auth}. {@code mode} 는 {@code EMAIL_PASSWORD} 고정, {@code refreshSupported} 는 false 고정
 * (API_SPEC 3.1~3.2 확정). TTL·실패 제한 수치는 서버 설정이다(D02).
 */
public record AuthConfig(int accessTokenTtlSeconds, int failedLoginLimit, int loginLockSeconds) {

    public static final String MODE = "EMAIL_PASSWORD";
    public static final boolean REFRESH_SUPPORTED = false;

    public AuthConfig {
        if (accessTokenTtlSeconds < 1 || failedLoginLimit < 1 || loginLockSeconds < 1) {
            throw new IllegalArgumentException("auth config values must be >= 1");
        }
    }
}
