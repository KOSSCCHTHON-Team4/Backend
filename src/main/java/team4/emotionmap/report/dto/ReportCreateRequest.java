package team4.emotionmap.report.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;
import team4.emotionmap.report.ReportReason;

public record ReportCreateRequest(
        @NotNull UUID memoryId,
        @NotNull ReportReason reason,
        String details
) {
}
