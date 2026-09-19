package team4.emotionmap.contracts.config;

import java.time.Instant;

/**
 * Internal catalog operation for appending a selection configuration history entry.
 *
 * <p>This is deliberately not an HTTP, command-line, or startup-seeding API. A repeated identical
 * config version is a no-op; reuse with different immutable payload is rejected.
 */
public interface SelectionConfigPublisher {

    SelectionConfigHistory.Snapshot append(
            String configVersion,
            Instant effectiveAt,
            int radiusMeters,
            String ruleVersion
    );
}
