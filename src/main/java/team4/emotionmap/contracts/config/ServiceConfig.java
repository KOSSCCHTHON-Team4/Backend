package team4.emotionmap.contracts.config;

import java.util.Objects;
import team4.emotionmap.contracts.geo.GeoPoint;

/**
 * 서버가 내려주는 고정 서비스 설정(API_SPEC 8.2). 반경·중심·한도는 모두 설정에서 주입되고
 * 사용자가 바꿀 수 없다. 배달 시각 {@code 09:00}/{@code Asia/Seoul} 은 확정 정책이라 상수다.
 */
public record ServiceConfig(
        String configVersion,
        int radiusMeters,
        GeoPoint demoCenter,
        ServiceLimits limits,
        AuthConfig auth
) {
    public static final String DELIVERY_TIME_ZONE = "Asia/Seoul";
    public static final String DELIVERY_LOCAL_TIME = "09:00";

    public ServiceConfig {
        Objects.requireNonNull(configVersion, "configVersion");
        Objects.requireNonNull(demoCenter, "demoCenter");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(auth, "auth");
        if (configVersion.isBlank()) {
            throw new IllegalArgumentException("configVersion must not be blank");
        }
        if (radiusMeters < 1) {
            throw new IllegalArgumentException("radiusMeters must be >= 1");
        }
    }
}
