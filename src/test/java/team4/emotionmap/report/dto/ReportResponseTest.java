package team4.emotionmap.report.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import team4.emotionmap.report.Report;
import team4.emotionmap.report.ReportReason;
import team4.emotionmap.report.ReportStatus;

class ReportResponseTest {
    @Test
    void projectsTheImmutableOpenSubmissionReceipt() {
        UUID reportId = UUID.randomUUID();
        UUID memoryId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2031-02-03T04:05:06Z");
        Report report = Report.builder()
                .id(reportId)
                .memoryId(memoryId)
                .reason(ReportReason.ABUSE)
                .details("private submission detail")
                .status(ReportStatus.RESOLVED)
                .createdAt(createdAt)
                .build();

        ReportResponse response = ReportResponse.from(report);

        assertThat(response.reportId()).isEqualTo(reportId);
        assertThat(response.memoryId()).isEqualTo(memoryId);
        assertThat(response.reason()).isEqualTo(ReportReason.ABUSE);
        assertThat(response.status()).isEqualTo(ReportStatus.OPEN);
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }
}
