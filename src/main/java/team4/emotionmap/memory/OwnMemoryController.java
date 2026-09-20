package team4.emotionmap.memory;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import team4.emotionmap.memory.dto.MemoryPageResponse;

@RestController
@RequestMapping("/v1/users/me/memories")
@RequiredArgsConstructor
public class OwnMemoryController {

    private final MemoryQueryService memoryQueryService;

    @GetMapping
    public MemoryPageResponse ownLetters(@AuthenticationPrincipal UUID userId,
                                         @RequestParam MultiValueMap<String, String> query) {
        MemoryPageQueryParameters.OwnLettersRequest request = MemoryPageQueryParameters.ownLetters(query);
        return memoryQueryService.ownLetters(userId, request.type(), request.cursor(), request.limit());
    }
}
