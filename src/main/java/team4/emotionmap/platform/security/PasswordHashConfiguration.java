package team4.emotionmap.platform.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Shared by the web application and the narrow, non-web provisioning bootstrap. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PasswordHashProperties.class)
public class PasswordHashConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder(PasswordHashProperties properties, Environment environment) {
        Integer strength = properties.bcryptStrength();
        if (strength == null || strength < 4 || strength > 31) {
            throw new IllegalStateException("Explicit BCrypt strength within the library range is required");
        }
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            String explicitStrength = environment.getProperty("PASSWORD_BCRYPT_STRENGTH");
            if (explicitStrength == null || explicitStrength.isBlank()) {
                throw new IllegalStateException("Production requires explicit PASSWORD_BCRYPT_STRENGTH");
            }
        }
        return new BCryptPasswordEncoder(strength);
    }
}
