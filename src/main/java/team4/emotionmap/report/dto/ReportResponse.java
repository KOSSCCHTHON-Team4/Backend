package team4.emotionmap.report.dto;

import java.time.Instant;
import java.util.UUID;
import team4.emotionmap.report.Report;
import team4.emotionmap.report.ReportReason;
import team4.emotionmap.report.ReportStatus;

public record ReportResponse(
        UUID id,
        UUID memoryId,
        ReportReason reason,
        String details,
        ReportStatus status,
        Instant createdAt
) {
    public static ReportResponse from(Report report) {
        return new ReportResponse(report.getId(), report.getMemoryId(), report.getReason(),
                report.getDetails(), report.getStatus(), report.getCreatedAt());
    }
}
