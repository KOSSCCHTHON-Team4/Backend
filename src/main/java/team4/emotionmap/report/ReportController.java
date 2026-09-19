package team4.emotionmap.report;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import team4.emotionmap.report.dto.ReportCreateRequest;
import team4.emotionmap.report.dto.ReportResponse;

/**
 * 신고 API. 인증 필요(JWT).
 *   POST /reports  -> 기억 신고 (중복 허용)
 * 현재 사용자 ID 는 인증 컨텍스트에서 얻는다.
 */
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse create(@AuthenticationPrincipal Long userId,
                                 @Valid @RequestBody ReportCreateRequest request) {
        return reportService.create(userId, request);
    }
}
