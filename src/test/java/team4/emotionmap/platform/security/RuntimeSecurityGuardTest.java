package team4.emotionmap.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import team4.emotionmap.contracts.ai.AnalysisPort;
import team4.emotionmap.contracts.ai.AnalysisRequest;
import team4.emotionmap.contracts.ai.AnalysisResult;
import team4.emotionmap.contracts.ai.ModerationPort;
import team4.emotionmap.contracts.ai.ModerationRequest;
import team4.emotionmap.contracts.ai.ModerationResult;
import team4.emotionmap.contracts.ai.PreferenceTieBreakPort;
import team4.emotionmap.contracts.ai.TieBreakRequest;
import team4.emotionmap.contracts.ai.TieBreakResult;

class RuntimeSecurityGuardTest {

    private static final String SAFE_JWT_SECRET = "runtime-guard-safe-jwt-secret-value-1234567890";
    private static final String SAFE_SIGNING_SECRET = "runtime-guard-safe-signing-secret-value-0987654321";
    private static final String EQUAL_SAFE_SECRET = "runtime-guard-safe-equal-secret-value-1122334455";
    private static final String SHORT_SECRET = "short";
    private static final String SAFE_ORIGIN = "https://app.example";

    @Test
    void guardConfigurationAcceptsSafeProductionValuesWithSyntheticPorts() {
        validGuardRunner()
                .withSystemProperties("spring.profiles.active=prod")
                .withPropertyValues(guardProperties(SAFE_JWT_SECRET, SAFE_SIGNING_SECRET, "real", SAFE_ORIGIN))
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void guardConfigurationRejectsUnsafeSecretWhenProdAndLocalAreActive() {
        String jwtSecret = JwtProperties.LOCAL_DEVELOPMENT_SECRET;
        validGuardRunner()
                .withSystemProperties("spring.profiles.active=prod,local")
                .withPropertyValues(guardProperties(jwtSecret, SAFE_SIGNING_SECRET, "real", SAFE_ORIGIN))
                .run(context -> assertGuardRejected(context, "app.jwt.secret", jwtSecret, SAFE_SIGNING_SECRET));
    }

    @Test
    void guardConfigurationRejectsUnsafeSecretWhenProdIsOnlyTheDefaultProfile() {
        String signingSecret = SigningProperties.LOCAL_DEVELOPMENT_SECRET;
        validGuardRunner()
                .withPropertyValues("spring.profiles.active=", "spring.profiles.default=prod")
                .withPropertyValues(guardProperties(SAFE_JWT_SECRET, signingSecret, "real", SAFE_ORIGIN))
                .run(context -> assertGuardRejected(context, "app.signing.secret", SAFE_JWT_SECRET, signingSecret));
    }

    @Test
    void guardConfigurationRejectsBlankSecretInEitherSlot() {
        assertProductionSecretRejected("", SAFE_SIGNING_SECRET, "app.jwt.secret");
        assertProductionSecretRejected(SAFE_JWT_SECRET, "", "app.signing.secret");
    }

    @Test
    void guardConfigurationRejectsShortSecretInEitherSlot() {
        assertProductionSecretRejected(SHORT_SECRET, SAFE_SIGNING_SECRET, "app.jwt.secret");
        assertProductionSecretRejected(SAFE_JWT_SECRET, SHORT_SECRET, "app.signing.secret");
    }

    @Test
    void guardConfigurationRejectsKnownDevelopmentSecretInEitherSlot() {
        for (String developmentSecret : List.of(
                JwtProperties.LOCAL_DEVELOPMENT_SECRET,
                SigningProperties.LOCAL_DEVELOPMENT_SECRET)) {
            assertProductionSecretRejected(developmentSecret, SAFE_SIGNING_SECRET, "app.jwt.secret");
            assertProductionSecretRejected(SAFE_JWT_SECRET, developmentSecret, "app.signing.secret");
        }
    }

    @Test
    void guardConfigurationRejectsSwappedDevelopmentSecrets() {
        String jwtSecret = SigningProperties.LOCAL_DEVELOPMENT_SECRET;
        String signingSecret = JwtProperties.LOCAL_DEVELOPMENT_SECRET;
        validGuardRunner()
                .withSystemProperties("spring.profiles.active=prod")
                .withPropertyValues(guardProperties(jwtSecret, signingSecret, "real", SAFE_ORIGIN))
                .run(context -> {
                    String failure = assertGuardRejected(context, "app.jwt.secret", jwtSecret, signingSecret);
                    assertThat(failure).contains("app.signing.secret");
                });
    }

    @Test
    void guardConfigurationRejectsEqualOtherwiseStrongSecrets() {
        validGuardRunner()
                .withSystemProperties("spring.profiles.active=prod")
                .withPropertyValues(guardProperties(EQUAL_SAFE_SECRET, EQUAL_SAFE_SECRET, "real", SAFE_ORIGIN))
                .run(context -> assertGuardRejected(
                        context,
                        "app.jwt.secret and app.signing.secret must differ",
                        EQUAL_SAFE_SECRET,
                        EQUAL_SAFE_SECRET));
    }

    @Test
    void guardConfigurationRejectsMissingAiProvider() {
        validGuardRunner()
                .withSystemProperties("spring.profiles.active=prod")
                .withPropertyValues(guardProperties(SAFE_JWT_SECRET, SAFE_SIGNING_SECRET, null, SAFE_ORIGIN))
                .run(context -> assertGuardRejected(
                        context,
                        "app.ai.provider must name a real provider",
                        SAFE_JWT_SECRET,
                        SAFE_SIGNING_SECRET));
    }

    @Test
    void guardConfigurationRejectsMockAiProvider() {
        validGuardRunner()
                .withSystemProperties("spring.profiles.active=prod")
                .withPropertyValues(guardProperties(SAFE_JWT_SECRET, SAFE_SIGNING_SECRET, "mock", SAFE_ORIGIN))
                .run(context -> assertGuardRejected(
                        context,
                        "app.ai.provider must name a real provider",
                        SAFE_JWT_SECRET,
                        SAFE_SIGNING_SECRET));
    }

    @Test
    void guardConfigurationRejectsEachMissingAiPort() {
        for (MissingPortScenario scenario : List.of(
                new MissingPortScenario("AnalysisPort", MissingAnalysisPortConfiguration.class),
                new MissingPortScenario("ModerationPort", MissingModerationPortConfiguration.class),
                new MissingPortScenario("PreferenceTieBreakPort", MissingPreferenceTieBreakPortConfiguration.class))) {
            guardRunner(scenario.portConfiguration())
                    .withSystemProperties("spring.profiles.active=prod")
                    .withPropertyValues(guardProperties(SAFE_JWT_SECRET, SAFE_SIGNING_SECRET, "real", SAFE_ORIGIN))
                    .run(context -> assertGuardRejected(
                            context,
                            "a real " + scenario.portName() + " bean is required",
                            SAFE_JWT_SECRET,
                            SAFE_SIGNING_SECRET));
        }
    }

    @Test
    void guardConfigurationRejectsJdkProxyAroundMockNamedTargetWhenOtherPortsAreRealStubs() {
        guardRunner(JdkProxyMockAnalysisPortConfiguration.class)
                .withSystemProperties("spring.profiles.active=prod")
                .withPropertyValues(guardProperties(SAFE_JWT_SECRET, SAFE_SIGNING_SECRET, "real", SAFE_ORIGIN))
                .run(context -> {
                    String failure = assertGuardRejected(
                            context,
                            "a real AnalysisPort bean is required",
                            SAFE_JWT_SECRET,
                            SAFE_SIGNING_SECRET);
                    assertThat(failure).doesNotContain("ModerationPort", "PreferenceTieBreakPort");
                });
    }

    @Test
    void guardConfigurationRejectsWildcardOriginWithOtherwiseValidLocalConfiguration() {
        validGuardRunner()
                .withSystemProperties("spring.profiles.active=local")
                .withPropertyValues(guardProperties(SAFE_JWT_SECRET, SAFE_SIGNING_SECRET, "real", "https://*.example"))
                .run(context -> assertOnlyWildcardOriginIsRejected(context));
    }

    @Test
    void guardConfigurationRejectsWildcardOriginWithOtherwiseValidProductionConfiguration() {
        validGuardRunner()
                .withSystemProperties("spring.profiles.active=prod")
                .withPropertyValues(guardProperties(SAFE_JWT_SECRET, SAFE_SIGNING_SECRET, "real", "https://*.example"))
                .run(context -> assertOnlyWildcardOriginIsRejected(context));
    }

    private static ApplicationContextRunner validGuardRunner() {
        return guardRunner(AllStubPortsConfiguration.class);
    }

    private static ApplicationContextRunner guardRunner(Class<?> portConfiguration) {
        return new ApplicationContextRunner()
                .withUserConfiguration(GuardConfiguration.class, portConfiguration);
    }

    private static String[] guardProperties(String jwtSecret, String signingSecret, String provider, String origin) {
        List<String> properties = new ArrayList<>(List.of(
                "app.jwt.secret=" + jwtSecret,
                "app.signing.secret=" + signingSecret,
                "app.cors.allowed-origins[0]=" + origin));
        if (provider != null) {
            properties.add("app.ai.provider=" + provider);
        }
        return properties.toArray(String[]::new);
    }

    private static void assertProductionSecretRejected(String jwtSecret, String signingSecret, String expectedProblem) {
        validGuardRunner()
                .withSystemProperties("spring.profiles.active=prod")
                .withPropertyValues(guardProperties(jwtSecret, signingSecret, "real", SAFE_ORIGIN))
                .run(context -> assertGuardRejected(context, expectedProblem, jwtSecret, signingSecret));
    }

    private static String assertGuardRejected(
            AssertableApplicationContext context,
            String expectedProblem,
            String jwtSecret,
            String signingSecret
    ) {
        assertThat(context).hasFailed();
        String failure = failureMessages(context.getStartupFailure());
        assertThat(failure).contains(expectedProblem);
        assertNoSecretLeakage(failure, jwtSecret, signingSecret);
        return failure;
    }

    private static void assertOnlyWildcardOriginIsRejected(AssertableApplicationContext context) {
        assertThat(context).hasFailed();
        String failure = failureMessages(context.getStartupFailure());
        assertThat(failure).contains("app.cors.allowed-origins must not contain wildcard origins")
                .doesNotContain("app.jwt.secret", "app.signing.secret", "app.ai.provider", "AnalysisPort",
                        "ModerationPort", "PreferenceTieBreakPort");
        assertNoSecretLeakage(failure, SAFE_JWT_SECRET, SAFE_SIGNING_SECRET);
    }

    private static String failureMessages(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            messages.append(current.getClass().getName())
                    .append(": ")
                    .append(current.getMessage())
                    .append('\n');
        }
        return messages.toString();
    }

    private static void assertNoSecretLeakage(String failure, String... secretValues) {
        for (String secret : secretValues) {
            if (secret != null && !secret.isEmpty()) {
                assertThat(failure).doesNotContain(secret);
            }
        }
    }

    private record MissingPortScenario(String portName, Class<?> portConfiguration) {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({JwtProperties.class, SigningProperties.class, CorsProperties.class})
    static class GuardConfiguration {

        @Bean
        RuntimeSecurityGuard runtimeSecurityGuard(
                Environment environment,
                JwtProperties jwt,
                SigningProperties signing,
                CorsProperties cors,
                ListableBeanFactory beanFactory
        ) {
            return new RuntimeSecurityGuard(environment, jwt, signing, cors, beanFactory);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AllStubPortsConfiguration {

        @Bean
        AnalysisPort analysisPort() {
            return new StubAnalysisPort();
        }

        @Bean
        ModerationPort moderationPort() {
            return new StubModerationPort();
        }

        @Bean
        PreferenceTieBreakPort preferenceTieBreakPort() {
            return new StubPreferenceTieBreakPort();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MissingAnalysisPortConfiguration {

        @Bean
        ModerationPort moderationPort() {
            return new StubModerationPort();
        }

        @Bean
        PreferenceTieBreakPort preferenceTieBreakPort() {
            return new StubPreferenceTieBreakPort();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MissingModerationPortConfiguration {

        @Bean
        AnalysisPort analysisPort() {
            return new StubAnalysisPort();
        }

        @Bean
        PreferenceTieBreakPort preferenceTieBreakPort() {
            return new StubPreferenceTieBreakPort();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MissingPreferenceTieBreakPortConfiguration {

        @Bean
        AnalysisPort analysisPort() {
            return new StubAnalysisPort();
        }

        @Bean
        ModerationPort moderationPort() {
            return new StubModerationPort();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class JdkProxyMockAnalysisPortConfiguration {

        @Bean
        AnalysisPort analysisPort() {
            ProxyFactory proxyFactory = new ProxyFactory();
            proxyFactory.setTarget(new MockAnalysisPort());
            proxyFactory.setInterfaces(AnalysisPort.class);
            proxyFactory.setProxyTargetClass(false);
            return (AnalysisPort) proxyFactory.getProxy();
        }

        @Bean
        ModerationPort moderationPort() {
            return new StubModerationPort();
        }

        @Bean
        PreferenceTieBreakPort preferenceTieBreakPort() {
            return new StubPreferenceTieBreakPort();
        }
    }

    private static final class StubAnalysisPort implements AnalysisPort {

        @Override
        public AnalysisResult analyze(AnalysisRequest request) {
            throw new UnsupportedOperationException("Guard configuration fixture does not analyze content");
        }
    }

    private static final class MockAnalysisPort implements AnalysisPort {

        @Override
        public AnalysisResult analyze(AnalysisRequest request) {
            throw new UnsupportedOperationException("Guard configuration fixture does not analyze content");
        }
    }

    private static final class StubModerationPort implements ModerationPort {

        @Override
        public ModerationResult moderate(ModerationRequest request) {
            throw new UnsupportedOperationException("Guard configuration fixture does not moderate content");
        }
    }

    private static final class StubPreferenceTieBreakPort implements PreferenceTieBreakPort {

        @Override
        public TieBreakResult rank(TieBreakRequest request) {
            throw new UnsupportedOperationException("Guard configuration fixture does not rank preferences");
        }
    }
}
