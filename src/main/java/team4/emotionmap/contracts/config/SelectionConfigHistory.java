package team4.emotionmap.contracts.config;

import java.time.Instant;
import java.util.Optional;

/** Immutable, cutoff-addressable selection configuration history. */
public interface SelectionConfigHistory {

    /**
     * Finds the configuration effective at {@code cutoff}: {@code effectiveAt <= cutoff}, ordered
     * by effective time descending and then revision descending. It never substitutes process-local
     * current configuration when no historical row exists.
     */
    Optional<Snapshot> findAt(Instant cutoff);

    record Snapshot(
            long revision,
            String configVersion,
            Instant effectiveAt,
            int radiusMeters,
            String ruleVersion
    ) {}
}
