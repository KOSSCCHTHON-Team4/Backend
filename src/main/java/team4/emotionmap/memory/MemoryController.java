package team4.emotionmap.memory;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Enumeration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.memory.DistributionType;
import team4.emotionmap.contracts.request.RequestCoordinator;
import team4.emotionmap.contracts.validation.StrictValues;
import team4.emotionmap.memory.dto.AnalyzeRequest;
import team4.emotionmap.memory.dto.AnalyzeResponse;
import team4.emotionmap.memory.dto.MemoryCreateRequest;
import team4.emotionmap.memory.dto.MemoryResponse;

@RestController
@RequestMapping("/v1/memories")
@RequiredArgsConstructor
public class MemoryController {
    private final MemoryService memoryService;
    private final MemoryAnalysisService memoryAnalysisService;
    private final LetterModerationService letterModerationService;
    private final MemoryQueryService memoryQueryService;

    @Operation(operationId = "analyzeMemory", summary = "본문의 분위기 4축·카테고리·근거·태그·안전/개인정보 AI 제안",
            description = "저장하지 않는다. 응답의 analysisToken 을 POST /v1/memories 에 그대로 넣으면 서버가 AI/USER 출처를 판정한다.")
    @PostMapping("/analyze")
    public AnalyzeResponse analyze(@AuthenticationPrincipal UUID userId, @Valid @RequestBody AnalyzeRequest request) {
        return memoryAnalysisService.analyze(userId, request);
    }

    @Operation(operationId = "createMemory", summary = "LETTER 또는 PRIVATE 생성",
            description = "LETTER 는 PENDING 으로 저장된 뒤 안전 검사를 거쳐 승인되면 배달 후보·취향 알림 매칭 대상이 된다. 201 ≠ 배달 성공.")
    @PostMapping
    public ResponseEntity<MemoryResponse> create(@AuthenticationPrincipal UUID userId,
                                                   @Valid @RequestBody MemoryCreateRequest request,
                                                   HttpServletRequest servletRequest) {
        rejectQueryFields(servletRequest);
        UUID key = requireSingleIdempotencyKey(servletRequest);
        MemoryService.CreateResult result = memoryService.create(userId, key, request);
        if (result.kind() == RequestCoordinator.CompletionKind.CREATED
                && request.type() == DistributionType.LETTER) {
            // 생성 트랜잭션은 이미 커밋됐다. 재생은 안전 검사를 다시 시작하지 않는다.
            letterModerationService.moderate(result.memoryId());
        }
        MemoryResponse response = memoryQueryService.get(userId, result.memoryId());
        HttpStatus status = result.kind() == RequestCoordinator.CompletionKind.CREATED
                ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{id}")
    public MemoryResponse get(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return memoryQueryService.get(userId, id);
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> image(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        MemoryService.ImageContent image = memoryService.image(userId, id);
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                    .contentType(MediaType.parseMediaType(image.mediaType()))
                    .contentLength(image.sizeBytes())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"image\"")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(new InputStreamResource(image.content()));
        } catch (RuntimeException exception) {
            try {
                image.content().close();
            } catch (IOException ignored) {
                // Do not attach storage-path-bearing IO details to the outward exception.
            }
            throw exception;
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        memoryService.delete(userId, id);
    }

    private static void rejectQueryFields(HttpServletRequest request) {
        String query = request.getQueryString();
        if (query != null && !query.isEmpty()) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST);
        }
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
