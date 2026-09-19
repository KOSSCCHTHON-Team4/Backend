package team4.emotionmap.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Private hashing configuration; the library range is not an operating recommendation. */
@ConfigurationProperties(prefix = "app.security.password")
public record PasswordHashProperties(Integer bcryptStrength) { }
