package team4.emotionmap.letter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Actual state of today's already-scheduled selection; this response never starts selection. */
public record TodayResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Status status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate serviceDate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant scheduledAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant nextScheduledAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant serverTime,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) PendingReason pendingReason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) ErrorReason errorReason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) LetterResponse delivery
) {
    public TodayResponse {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(serviceDate, "serviceDate");
        Objects.requireNonNull(scheduledAt, "scheduledAt");
        Objects.requireNonNull(nextScheduledAt, "nextScheduledAt");
        Objects.requireNonNull(serverTime, "serverTime");
        if ((status == Status.DELIVERED) != (delivery != null)) {
            throw new IllegalArgumentException("delivery is required only for DELIVERED");
        }
        if ((status == Status.PENDING) != (pendingReason != null)) {
            throw new IllegalArgumentException("pendingReason is required only for PENDING");
        }
        if ((status == Status.RETRYING || status == Status.ERROR) != (errorReason != null)) {
            throw new IllegalArgumentException("errorReason is required for retrying/error states");
        }
        if (pendingReason != null && errorReason != null) {
            throw new IllegalArgumentException("today response cannot have both reasons");
        }
    }

    public enum Status {
        PENDING,
        NO_CANDIDATE,
        DELIVERED,
        RETRYING,
        ERROR
    }

    public enum PendingReason {
        BEFORE_SCHEDULE,
        FIRST_DELIVERY_TOMORROW,
        SCHEDULE_DELAYED,
        PROCESSING
    }

    public enum ErrorReason {
        RETRYABLE_FAILURE,
        SELECTION_STATE_INCONSISTENT,
        PROCESSING_FAILURE
    }
}
