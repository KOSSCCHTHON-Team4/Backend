package team4.emotionmap.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import team4.emotionmap.contracts.config.ServiceConfig;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;

class ServiceConfigProviderTest {

    static ServiceConfigProperties mockProperties() {
        return new ServiceConfigProperties("mock-config-v1", 1000,
                new ServiceConfigProperties.Center(37.6109, 126.9977),
                new ServiceConfigProperties.Limits(3000, 1000, 1000, 5_242_880L, 6000, 6000, 20_000_000L,
                        1800, 900, 20, 20, 50, 200),
                new ServiceConfigProperties.Auth(3600, 5, 60));
    }

    @Test
    void completePropertiesProduceConfig() {
        ServiceConfigProvider provider = new ServiceConfigProvider(mockProperties());
        ServiceConfig config = provider.current();
        assertThat(config.radiusMeters()).isEqualTo(1000);
        assertThat(config.limits().analysisTtlSeconds()).isEqualTo(900);
        assertThat(config.auth().failedLoginLimit()).isEqualTo(5);
        assertThat(provider.missingKeys()).isEmpty();
    }

    @Test
    void anyMissingKeyMakesConfigUnavailable() {
        ServiceConfigProperties p = mockProperties();
        ServiceConfigProvider provider = new ServiceConfigProvider(new ServiceConfigProperties(
                p.configVersion(), null, p.demoCenter(), p.limits(), p.auth()));
        assertThat(provider.missingKeys()).containsExactly("radius-meters");
        assertThat(catchThrowableOfType(ContractError.class, provider::current).code())
                .isEqualTo(ErrorCode.CONFIGURATION_UNAVAILABLE);

        ServiceConfigProvider none = new ServiceConfigProvider(new ServiceConfigProperties(null, null, null, null, null));
        assertThat(none.missingKeys()).contains("config-version", "radius-meters", "demo-center.lat", "limits.*", "auth.*");
    }

    @Test
    void invalidRangeIsAlsoUnavailable() {
        ServiceConfigProperties p = mockProperties();
        ServiceConfigProvider provider = new ServiceConfigProvider(new ServiceConfigProperties(
                p.configVersion(), 0, p.demoCenter(), p.limits(), p.auth()));
        assertThat(provider.missingKeys()).hasSize(1);
        assertThat(provider.missingKeys().get(0)).startsWith("(invalid)");
    }

    /** {@code ${ENV:}} 형태의 빈 값이 숫자 필드에서 null 로 바인딩되는지(기본값 주입 없음) 확인한다. */
    @Test
    void emptyEnvironmentValueBindsToNull() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "app.service.config-version", "",
                "app.service.radius-meters", "",
                "app.service.demo-center.lat", "",
                "app.service.limits.image-max-bytes", "")));
        ServiceConfigProperties bound = binder.bind("app.service", Bindable.of(ServiceConfigProperties.class)).get();
        assertThat(bound.radiusMeters()).isNull();
        assertThat(bound.demoCenter() == null || bound.demoCenter().lat() == null).isTrue();
        assertThat(bound.limits() == null || bound.limits().imageMaxBytes() == null).isTrue();
        ServiceConfigProvider provider = new ServiceConfigProvider(bound);
        assertThat(provider.missingKeys()).contains("config-version", "radius-meters");
    }
}
