package team4.emotionmap.contracts.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ServiceTimeTest {

    @Test
    void serviceDateUsesKstAtMidnightBoundary() {
        Instant beforeMidnight = Instant.parse("2026-01-01T14:59:59.999999999Z");
        Instant atMidnight = Instant.parse("2026-01-01T15:00:00Z");

        assertThat(ServiceTime.serviceDate(beforeMidnight)).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(ServiceTime.serviceDate(atMidnight)).isEqualTo(LocalDate.of(2026, 1, 2));
    }

    @Test
    void nextScheduledAtTreatsExactlyNineAsNextDay() {
        Instant cutoff = Instant.parse("2026-01-02T00:00:00Z");

        assertThat(ServiceTime.nextScheduledAt(cutoff.minusNanos(1))).isEqualTo(cutoff);
        assertThat(ServiceTime.nextScheduledAt(cutoff)).isEqualTo(cutoff.plus(24, java.time.temporal.ChronoUnit.HOURS));
    }

    @Test
    void cutoffIsExplicitlyKstIndependentOfJvmDefaultZone() {
        LocalDate date = LocalDate.of(2026, 6, 15);

        assertThat(ServiceTime.cutoff(date)).isEqualTo(date.atTime(9, 0).atOffset(ZoneOffset.ofHours(9)).toInstant());
    }
}
