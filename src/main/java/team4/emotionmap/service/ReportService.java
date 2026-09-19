package team4.emotionmap.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team4.emotionmap.domain.Report;
import team4.emotionmap.dto.ReportCreateRequest;
import team4.emotionmap.dto.ReportResponse;
import team4.emotionmap.repository.ReportRepository;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;

    /** 신고 생성. 중복 신고 허용(제약 없음). */
    @Transactional
    public ReportResponse create(Long reporterId, ReportCreateRequest req) {
        Report saved = reportRepository.save(Report.builder()
                .reporterId(reporterId)
                .memoryId(req.memoryId())
                .reason(req.reason())
                .build());
        return ReportResponse.from(saved);
    }
}
