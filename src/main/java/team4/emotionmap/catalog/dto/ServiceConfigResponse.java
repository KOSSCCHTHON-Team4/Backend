package team4.emotionmap.catalog.dto;

import team4.emotionmap.contracts.config.AuthConfig;
import team4.emotionmap.contracts.config.ServiceConfig;
import team4.emotionmap.contracts.config.ServiceLimits;

/** API_SPEC 8.2 {@code ServiceConfig}. 필드 이름·순서는 명세 예시와 같다. */
public record ServiceConfigResponse(
        String configVersion,
        int radiusMeters,
        Coordinates demoCenter,
        String deliveryTimeZone,
        String deliveryLocalTime,
        ServiceLimits limits,
        Auth auth
) {
    public record Coordinates(double lat, double lng) {
    }

    public record Auth(String mode, boolean refreshSupported, int accessTokenTtlSeconds,
                       int failedLoginLimit, int loginLockSeconds) {
    }

    public static ServiceConfigResponse from(ServiceConfig config) {
        AuthConfig auth = config.auth();
        return new ServiceConfigResponse(
                config.configVersion(),
                config.radiusMeters(),
                new Coordinates(config.demoCenter().lat(), config.demoCenter().lng()),
                ServiceConfig.DELIVERY_TIME_ZONE,
                ServiceConfig.DELIVERY_LOCAL_TIME,
                config.limits(),
                new Auth(AuthConfig.MODE, AuthConfig.REFRESH_SUPPORTED, auth.accessTokenTtlSeconds(),
                        auth.failedLoginLimit(), auth.loginLockSeconds()));
    }
}
