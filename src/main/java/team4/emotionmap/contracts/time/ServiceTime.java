package team4.emotionmap.contracts.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Pure service-calendar calculations in Korea Standard Time, independent of the JVM default time zone.
 * A service day starts at KST midnight and its scheduled cutoff is exactly 09:00 KST.
 */
public final class ServiceTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final int CUTOFF_HOUR = 9;

    private ServiceTime() {}

    /** Returns the KST calendar date containing {@code instant}. */
    public static LocalDate serviceDate(Instant instant) {
        return Objects.requireNonNull(instant, "instant").atZone(ZONE).toLocalDate();
    }

    /** Returns this service date's cutoff, exactly 09:00 KST, as an {@link Instant}. */
    public static Instant cutoff(LocalDate serviceDate) {
        return Objects.requireNonNull(serviceDate, "serviceDate").atTime(CUTOFF_HOUR, 0).atZone(ZONE).toInstant();
    }

    /**
     * Returns today's KST cutoff when {@code now} is strictly before it; at the cutoff and later,
     * returns the next KST service day's cutoff.
     */
    public static Instant nextScheduledAt(Instant now) {
        LocalDate today = serviceDate(now);
        Instant todayCutoff = cutoff(today);
        return now.isBefore(todayCutoff) ? todayCutoff : cutoff(today.plusDays(1));
    }
}
