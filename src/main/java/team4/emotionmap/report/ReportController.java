package team4.emotionmap.report;

import jakarta.validation.Valid;
import java.util.UUID;
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

@RestController
@RequestMapping("/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse create(@AuthenticationPrincipal UUID userId,
                                 @Valid @RequestBody ReportCreateRequest request) {
        return reportService.create(userId, request);
    }
}
