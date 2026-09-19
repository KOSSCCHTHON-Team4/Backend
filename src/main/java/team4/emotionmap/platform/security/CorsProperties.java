package team4.emotionmap.platform.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Explicit cross-origin allowlist. Empty means cross-origin browser requests are not allowed. */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
