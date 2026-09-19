package team4.emotionmap.platform.security;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import team4.emotionmap.contracts.ai.AnalysisPort;
import team4.emotionmap.contracts.ai.ModerationPort;
import team4.emotionmap.contracts.ai.PreferenceTieBreakPort;

/** Fails closed when a production profile is combined with development security or AI wiring. */
@Component
public class RuntimeSecurityGuard implements SmartInitializingSingleton {

    private static final int MINIMUM_SECRET_BYTES = 32;

    private final Environment environment;
    private final JwtProperties jwt;
    private final SigningProperties signing;
    private final CorsProperties cors;
    private final ListableBeanFactory beanFactory;

    public RuntimeSecurityGuard(Environment environment, JwtProperties jwt, SigningProperties signing,
                                CorsProperties cors, ListableBeanFactory beanFactory) {
        this.environment = environment;
        this.jwt = jwt;
        this.signing = signing;
        this.cors = cors;
        this.beanFactory = beanFactory;
    }

    @Override
    public void afterSingletonsInstantiated() {
        rejectWildcardOrigins();
        if (!isProductionProfileActive()) {
            return;
        }

        List<String> invalid = new ArrayList<>();
        validateSecret(jwt.secret(), "app.jwt.secret", invalid);
        validateSecret(signing.secret(), "app.signing.secret", invalid);
        if (same(jwt.secret(), signing.secret())) {
            invalid.add("app.jwt.secret and app.signing.secret must differ");
        }
        validateAi(invalid);
        if (!invalid.isEmpty()) {
            throw new IllegalStateException("Production runtime security configuration is invalid: "
                    + String.join("; ", invalid));
        }
    }

    private void rejectWildcardOrigins() {
        if (cors.allowedOrigins().stream().map(String::trim).anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalStateException("app.cors.allowed-origins must not contain wildcard origins");
        }
    }

    private boolean isProductionProfileActive() {
        return environment.matchesProfiles("prod");
    }

    private static void validateSecret(String secret, String property, List<String> invalid) {
        if (secret == null || secret.isBlank()) {
            invalid.add(property + " is required");
        } else if (secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_SECRET_BYTES) {
            invalid.add(property + " must be at least " + MINIMUM_SECRET_BYTES + " bytes");
        } else if (isKnownDevelopmentSecret(secret)) {
            invalid.add(property + " must not use a local development secret");
        }
    }

    private static boolean isKnownDevelopmentSecret(String secret) {
        return JwtProperties.LOCAL_DEVELOPMENT_SECRET.equals(secret)
                || SigningProperties.LOCAL_DEVELOPMENT_SECRET.equals(secret);
    }

    private void validateAi(List<String> invalid) {
        String provider = environment.getProperty("app.ai.provider");
        if (provider == null || provider.isBlank() || "mock".equalsIgnoreCase(provider)) {
            invalid.add("app.ai.provider must name a real provider");
        }
        validatePort(AnalysisPort.class, "AnalysisPort", invalid);
        validatePort(ModerationPort.class, "ModerationPort", invalid);
        validatePort(PreferenceTieBreakPort.class, "PreferenceTieBreakPort", invalid);
    }

    private <T> void validatePort(Class<T> port, String name, List<String> invalid) {
        Map<String, T> adapters = beanFactory.getBeansOfType(port);
        if (adapters.isEmpty() || adapters.values().stream().anyMatch(this::isMockAdapter)) {
            invalid.add("a real " + name + " bean is required");
        }
    }

    private boolean isMockAdapter(Object adapter) {
        return AopUtils.getTargetClass(adapter).getSimpleName().startsWith("Mock");
    }

    private static boolean same(String first, String second) {
        return first != null && second != null && first.equals(second);
    }
}
