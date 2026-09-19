package team4.emotionmap.memory;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
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
import team4.emotionmap.contracts.memory.DistributionType;
import team4.emotionmap.media.StoredImage;
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

    @Operation(operationId = "analyzeMemory", summary = "본문의 분위기 4축·카테고리·근거·태그·안전/개인정보 AI 제안",
            description = "저장하지 않는다. 응답의 analysisToken 을 POST /v1/memories 에 그대로 넣으면 서버가 AI/USER 출처를 판정한다.")
    @PostMapping("/analyze")
    public AnalyzeResponse analyze(@AuthenticationPrincipal UUID userId, @Valid @RequestBody AnalyzeRequest request) {
        return memoryAnalysisService.analyze(userId, request);
    }

    @Operation(operationId = "createMemory", summary = "LETTER 또는 PRIVATE 생성",
            description = "LETTER 는 PENDING 으로 저장된 뒤 안전 검사를 거쳐 승인되면 배달 후보·취향 알림 매칭 대상이 된다. 201 ≠ 배달 성공.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemoryResponse create(@AuthenticationPrincipal UUID userId,
                                 @Valid @RequestBody MemoryCreateRequest request) {
        MemoryResponse created = memoryService.create(userId, request);
        if (created.distributionType() == DistributionType.LETTER) {
            // 생성 트랜잭션은 이미 커밋됐다. 검사 실패는 201 을 바꾸지 않고 재시도 작업에 맡긴다.
            letterModerationService.moderate(created.id());
            return memoryService.get(userId, created.id());
        }
        return created;
    }

    @GetMapping("/{id}")
    public MemoryResponse get(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return memoryService.get(userId, id);
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> image(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        StoredImage image = memoryService.image(userId, id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(image.contentType()))
                .body(new FileSystemResource(image.path()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        memoryService.delete(userId, id);
    }
}
