package team4.emotionmap.memory;

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
import team4.emotionmap.media.StoredImage;
import team4.emotionmap.memory.dto.MemoryCreateRequest;
import team4.emotionmap.memory.dto.MemoryResponse;

@RestController
@RequestMapping("/v1/memories")
@RequiredArgsConstructor
public class MemoryController {
    private final MemoryService memoryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemoryResponse create(@AuthenticationPrincipal UUID userId,
                                 @Valid @RequestBody MemoryCreateRequest request) {
        return memoryService.create(userId, request);
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
