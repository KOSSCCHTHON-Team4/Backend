package team4.emotionmap.contracts.config;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;

@ConfigurationProperties(prefix = "app.pagination")
public record PaginationProperties(@DurationUnit(ChronoUnit.SECONDS) Duration cursorTtl) {
    public Instant requireCursorExpiry(Instant issuedAt) {
        Objects.requireNonNull(issuedAt, "issuedAt");
        if (cursorTtl == null || cursorTtl.isZero() || cursorTtl.isNegative()) {
            throw ContractError.of(ErrorCode.CONFIGURATION_UNAVAILABLE);
        }
        try {
            Instant deadline = issuedAt.plus(cursorTtl);
            // Signed claims use epoch seconds; never shorten the configured lifetime.
            return deadline.getNano() == 0
                    ? deadline
                    : deadline.truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);
        } catch (DateTimeException | ArithmeticException error) {
            throw ContractError.of(ErrorCode.CONFIGURATION_UNAVAILABLE);
        }
    }
}
