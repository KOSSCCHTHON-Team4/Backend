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
@RequestMapping("/v1/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final MemoryQueryService memoryQueryService;

    @GetMapping
    public MemoryPageResponse bookmarks(@AuthenticationPrincipal UUID userId,
                                        @RequestParam MultiValueMap<String, String> query) {
        MemoryPageQueryParameters.PageRequest page = MemoryPageQueryParameters.page(query);
        return memoryQueryService.bookmarks(userId, page.cursor(), page.limit());
    }
}
