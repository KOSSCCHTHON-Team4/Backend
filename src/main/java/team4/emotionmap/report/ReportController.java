package team4.emotionmap.report;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Enumeration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.request.RequestCoordinator;
import team4.emotionmap.contracts.validation.StrictValues;
import team4.emotionmap.report.dto.ReportCreateRequest;
import team4.emotionmap.report.dto.ReportResponse;

@RestController
@RequestMapping("/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(operationId = "createReport", summary = "열람 가능한 경험 신고 접수",
            description = "query parameter는 허용하지 않는다. Idempotency-Key는 정확히 하나의 UUID여야 하며, 같은 정규화 요청은 최초 OPEN 접수증을 재생한다.")
    @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true,
            description = "정확히 하나의 UUID. 누락은 IDEMPOTENCY_KEY_REQUIRED, 중복·쉼표 포함·잘못된 UUID는 INVALID_REQUEST.",
            schema = @Schema(type = "string", format = "uuid"))
    @PostMapping
    public ResponseEntity<ReportResponse> create(@AuthenticationPrincipal UUID userId,
                                                  @Valid @RequestBody ReportCreateRequest request,
                                                  HttpServletRequest servletRequest) {
        String query = servletRequest.getQueryString();
        if (query != null && !query.isEmpty()) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST);
        }
        UUID key = requireSingleIdempotencyKey(servletRequest);
        ReportService.CreateResult result = reportService.create(userId, key, request);
        HttpStatus status = result.kind() == RequestCoordinator.CompletionKind.CREATED
                ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.response());
    }

    private static UUID requireSingleIdempotencyKey(HttpServletRequest request) {
        Enumeration<String> values = request.getHeaders("Idempotency-Key");
        if (values == null || !values.hasMoreElements()) {
            throw ContractError.of(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        String value = values.nextElement();
        if (values.hasMoreElements() || value == null || value.indexOf(',') >= 0) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST);
        }
        return StrictValues.requireUuid(value, "idempotencyKey", ErrorCode.INVALID_REQUEST);
    }
}
