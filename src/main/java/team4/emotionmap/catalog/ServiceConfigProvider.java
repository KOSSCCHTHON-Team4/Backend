package team4.emotionmap.catalog;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import team4.emotionmap.contracts.config.AuthConfig;
import team4.emotionmap.contracts.config.ServiceConfig;
import team4.emotionmap.contracts.config.ServiceConfigSource;
import team4.emotionmap.contracts.config.ServiceLimits;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.geo.GeoPoint;

/**
 * {@link ServiceConfigSource} 구현. 시작 시 한 번 검증해 결과를 고정한다.
 * 누락·범위 오류가 있으면 부팅은 되지만 {@link #current()} 는 항상 503 을 던지고, 누락 키 목록은
 * 서버 로그에만 남는다(응답에는 넣지 않음). 조용히 기본 반경을 넣지 않는다(API_SPEC 2.1).
 */
@Slf4j
@Component
public class ServiceConfigProvider implements ServiceConfigSource {

    private final ServiceConfig config;       // null 이면 사용 불가
    private final List<String> missingKeys;

    public ServiceConfigProvider(ServiceConfigProperties properties) {
        List<String> missing = new ArrayList<>();
        ServiceConfig built = null;
        try {
            built = build(properties, missing);
        } catch (RuntimeException e) {
            missing.add("(invalid) " + e.getMessage());
        }
        this.config = missing.isEmpty() ? built : null;
        this.missingKeys = List.copyOf(missing);
        if (config == null) {
            log.error("service config unavailable — missing or invalid keys under app.service: {}", missingKeys);
        } else {
            log.info("service config loaded: configVersion={}", config.configVersion());
        }
    }

    @Override
    public ServiceConfig current() {
        if (config == null) {
            throw ContractError.of(ErrorCode.CONFIGURATION_UNAVAILABLE);
        }
        return config;
    }

    /** 운영 점검용. 응답에 노출하지 않는다. */
    public List<String> missingKeys() {
        return missingKeys;
    }

    static ServiceConfig build(ServiceConfigProperties p, List<String> missing) {
        if (p == null) {
            missing.add("app.service");
            return null;
        }
        String configVersion = require(p.configVersion(), "config-version", missing);
        Integer radius = require(p.radiusMeters(), "radius-meters", missing);

        Double lat = p.demoCenter() == null ? null : p.demoCenter().lat();
        Double lng = p.demoCenter() == null ? null : p.demoCenter().lng();
        require(lat, "demo-center.lat", missing);
        require(lng, "demo-center.lng", missing);

        ServiceConfigProperties.Limits l = p.limits();
        if (l == null) {
            missing.add("limits.*");
        } else {
            require(l.memoryContentMaxCodePoints(), "limits.memory-content-max-code-points", missing);
            require(l.preferenceDescriptionMaxCodePoints(), "limits.preference-description-max-code-points", missing);
            require(l.reportDetailsMaxCodePoints(), "limits.report-details-max-code-points", missing);
            require(l.imageMaxBytes(), "limits.image-max-bytes", missing);
            require(l.imageMaxWidth(), "limits.image-max-width", missing);
            require(l.imageMaxHeight(), "limits.image-max-height", missing);
            require(l.imageMaxPixels(), "limits.image-max-pixels", missing);
            require(l.imageUploadTtlSeconds(), "limits.image-upload-ttl-seconds", missing);
            require(l.analysisTtlSeconds(), "limits.analysis-ttl-seconds", missing);
            require(l.dailyDirectMemoryLimit(), "limits.daily-direct-memory-limit", missing);
            require(l.defaultPageLimit(), "limits.default-page-limit", missing);
            require(l.maxPageLimit(), "limits.max-page-limit", missing);
            require(l.maxMapPageLimit(), "limits.max-map-page-limit", missing);
        }
        ServiceConfigProperties.Auth a = p.auth();
        if (a == null) {
            missing.add("auth.*");
        } else {
            require(a.accessTokenTtlSeconds(), "auth.access-token-ttl-seconds", missing);
            require(a.failedLoginLimit(), "auth.failed-login-limit", missing);
            require(a.loginLockSeconds(), "auth.login-lock-seconds", missing);
        }
        if (!missing.isEmpty()) {
            return null;
        }
        ServiceLimits limits = new ServiceLimits(
                l.memoryContentMaxCodePoints(), l.preferenceDescriptionMaxCodePoints(), l.reportDetailsMaxCodePoints(),
                l.imageMaxBytes(), l.imageMaxWidth(), l.imageMaxHeight(), l.imageMaxPixels(),
                l.imageUploadTtlSeconds(), l.analysisTtlSeconds(), l.dailyDirectMemoryLimit(),
                l.defaultPageLimit(), l.maxPageLimit(), l.maxMapPageLimit());
        AuthConfig auth = new AuthConfig(a.accessTokenTtlSeconds(), a.failedLoginLimit(), a.loginLockSeconds());
        return new ServiceConfig(configVersion, radius, new GeoPoint(lat, lng), limits, auth);
    }

    private static <T> T require(T value, String key, List<String> missing) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            missing.add(key);
        }
        return value;
    }
}
