package team4.emotionmap.report;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team4.emotionmap.account.AccountAccessService;
import team4.emotionmap.memory.MemoryAccessService;
import team4.emotionmap.report.dto.ReportCreateRequest;
import team4.emotionmap.report.dto.ReportResponse;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final AccountAccessService accountAccessService;
    private final MemoryAccessService memoryAccessService;

    /** 신고 생성. 중복 신고 허용(제약 없음). */
    @Transactional
    public ReportResponse create(UUID reporterId, ReportCreateRequest req) {
        accountAccessService.requireActive(reporterId);
        memoryAccessService.requireReadable(reporterId, req.memoryId());
        Report saved = reportRepository.save(Report.builder()
                .reporterId(reporterId)
                .memoryId(req.memoryId())
                .reason(req.reason())
                .details(req.details() == null || req.details().isBlank() ? null : req.details().strip())
                .build());
        return ReportResponse.from(saved);
    }
}
