package team4.emotionmap.report.dto;

import java.time.OffsetDateTime;
import team4.emotionmap.report.Report;

/**
 * 신고 응답 DTO.
 */
public record ReportResponse(
        Long id,
        Long reporterId,
        Long memoryId,
        String reason,
        OffsetDateTime createdAt
) {
    public static ReportResponse from(Report r) {
        return new ReportResponse(
                r.getId(), r.getReporterId(), r.getMemoryId(), r.getReason(), r.getCreatedAt());
    }
}
