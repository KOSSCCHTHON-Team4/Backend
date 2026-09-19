package team4.emotionmap.account;

import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import team4.emotionmap.platform.security.PasswordHashConfiguration;

/**
 * Explicit non-web context for account provisioning. It deliberately has no component scan, web security, Flyway, or
 * application-profile dependency; {@link AccountProvisioningCli} supplies all runtime properties.
 */
@EnableAutoConfiguration
@EntityScan(basePackageClasses = {User.class, EmailPasswordCredential.class, LoginAttemptLimit.class})
@Import(PasswordHashConfiguration.class)
public class AccountProvisioningBootstrap {

    @Bean
    LoginAttemptLimitRepository loginAttemptLimitRepository(EntityManager entityManager) {
        return new LoginAttemptLimitRepository(entityManager);
    }

    @Bean
    AccountProvisioningService accountProvisioningService(EntityManager entityManager,
                                                           LoginAttemptLimitRepository attemptLimitRepository,
                                                           PasswordEncoder passwordEncoder,
                                                           Validator validator,
                                                           PlatformTransactionManager transactionManager) {
        return new AccountProvisioningService(entityManager, attemptLimitRepository, passwordEncoder, validator,
                transactionManager);
    }
}
