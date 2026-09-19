package team4.emotionmap.account;

import org.springframework.boot.context.properties.ConfigurationProperties;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;

/** Private login-limiter settings. Missing or invalid values fail login closed rather than gaining a default. */
@ConfigurationProperties(prefix = "app.account.login")
public record LoginPolicyProperties(String failedAttemptWindowSeconds) {

    public int requiredFailedAttemptWindowSeconds() {
        if (failedAttemptWindowSeconds == null || failedAttemptWindowSeconds.isBlank()) {
            throw ContractError.of(ErrorCode.CONFIGURATION_UNAVAILABLE);
        }
        try {
            long seconds = Long.parseLong(failedAttemptWindowSeconds);
            if (seconds < 1 || seconds > Integer.MAX_VALUE) {
                throw ContractError.of(ErrorCode.CONFIGURATION_UNAVAILABLE);
            }
            return (int) seconds;
        } catch (NumberFormatException ignored) {
            throw ContractError.of(ErrorCode.CONFIGURATION_UNAVAILABLE);
        }
    }
}
