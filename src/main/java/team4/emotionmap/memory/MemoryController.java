package team4.emotionmap.memory;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import team4.emotionmap.memory.dto.MemoryCreateRequest;
import team4.emotionmap.memory.dto.MemoryResponse;

/**
 * 기억 API. 인증 필요(JWT).
 *   POST   /memories                -> 저장 (서버가 모더레이션·감정태그·임베딩 채움: AI 포트)
 *   GET    /memories/{id}           -> 단건 조회
 *   DELETE /memories/{id}           -> 삭제(하드)
 */
@RestController
@RequestMapping("/memories")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryService memoryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemoryResponse create(@Valid @RequestBody MemoryCreateRequest request) {
        return memoryService.create(request);
    }

    @GetMapping("/{id}")
    public MemoryResponse get(@PathVariable Long id) {
        return memoryService.get(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        memoryService.delete(id);
    }
}
