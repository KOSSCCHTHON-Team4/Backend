package team4.emotionmap.catalog;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.service.*} 바인딩(A03/D03). 모든 필드는 <b>래퍼 타입</b>이라 누락되면 null 로 남고,
 * {@link ServiceConfigProvider} 가 503 CONFIGURATION_UNAVAILABLE 로 바꾼다. 여기에 기본값을 넣지 않는다.
 * 빈 문자열 환경변수({@code ${X:}})는 숫자 필드에서 null 로 바인딩된다.
 */
@ConfigurationProperties(prefix = "app.service")
public record ServiceConfigProperties(
        String configVersion,
        Integer radiusMeters,
        Center demoCenter,
        Limits limits,
        Auth auth
) {
    public record Center(Double lat, Double lng) {
    }

    public record Limits(
            Integer memoryContentMaxCodePoints,
            Integer preferenceDescriptionMaxCodePoints,
            Integer reportDetailsMaxCodePoints,
            Long imageMaxBytes,
            Integer imageMaxWidth,
            Integer imageMaxHeight,
            Long imageMaxPixels,
            Integer imageUploadTtlSeconds,
            Integer analysisTtlSeconds,
            Integer dailyDirectMemoryLimit,
            Integer defaultPageLimit,
            Integer maxPageLimit,
            Integer maxMapPageLimit
    ) {
    }

    public record Auth(Integer accessTokenTtlSeconds, Integer failedLoginLimit, Integer loginLockSeconds) {
    }
}
