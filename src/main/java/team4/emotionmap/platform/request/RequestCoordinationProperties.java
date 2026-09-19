package team4.emotionmap.platform.request;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Durable request-ownership lease configuration.
 *
 * <p>No operating default is supplied: a missing, non-positive, or unrepresentable value closes only
 * new claims and expired-claim recovery with {@code CONFIGURATION_UNAVAILABLE}. Existing completed and
 * active records remain readable from their persisted state.
 */
@ConfigurationProperties(prefix = "app.request-coordination")
public record RequestCoordinationProperties(Duration leaseDuration) {
}
