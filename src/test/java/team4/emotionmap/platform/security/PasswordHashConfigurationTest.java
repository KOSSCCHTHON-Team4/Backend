package team4.emotionmap.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordHashConfigurationTest {

    @Test
    void productionAndLocalProfilesRejectSyntheticFallbackEvenWhenItIsWithinTechnicalRange() {
        hashingRunner()
                .withSystemProperties("spring.profiles.active=prod,local")
                .withPropertyValues("app.security.password.bcrypt-strength=4")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void explicitProductionStrengthCreatesSharedEncoderThatMatchesTheOriginalPassword() {
        hashingRunner()
                .withSystemProperties("spring.profiles.active=prod,local", "PASSWORD_BCRYPT_STRENGTH=4")
                .withPropertyValues("app.security.password.bcrypt-strength=4")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(PasswordEncoder.class);

                    PasswordEncoder encoder = context.getBean(PasswordEncoder.class);
                    String password = "credential value";
                    String encoded = encoder.encode(password);

                    assertThat(encoded).isNotBlank();
                    assertThat(encoder.matches(password, encoded)).isTrue();
                    assertThat(encoder.matches(password + "!", encoded)).isFalse();
                });
    }

    @Test
    void missingStrengthFailsBeforeAnEncoderCanBeCreated() {
        hashingRunner().run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest(name = "bcrypt strength {0} is rejected")
    @ValueSource(strings = {"3", "32", "not-a-number"})
    void invalidTechnicalStrengthFailsBeforeAnEncoderCanBeCreated(String strength) {
        hashingRunner()
                .withPropertyValues("app.security.password.bcrypt-strength=" + strength)
                .run(context -> assertThat(context).hasFailed());
    }

    private static ApplicationContextRunner hashingRunner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(PasswordHashConfiguration.class);
    }
}
