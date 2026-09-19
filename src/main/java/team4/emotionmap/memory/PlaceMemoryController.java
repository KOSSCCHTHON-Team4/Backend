package team4.emotionmap.memory;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team4.emotionmap.memory.dto.MemoryResponse;

/** URL은 장소 하위지만, 기억 목록의 조회 정책은 기억 모듈이 소유한다. */
@RestController
@RequestMapping("/v1/places/{id}/memories")
@RequiredArgsConstructor
public class PlaceMemoryController {

    private final MemoryService memoryService;

    @GetMapping
    public List<MemoryResponse> memories(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return memoryService.findByPlace(userId, id);
    }
}
