package team4.emotionmap.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import team4.emotionmap.notification.dto.PreferenceRequest;
import team4.emotionmap.notification.dto.PreferenceResponse;

/** 취향 알림 설정 API(기획 §4). 온보딩 취향(PATCH /v1/users/me/preferences)과 다른 자원이다. */
@Tag(name = "preferences", description = "키워드 카드 기반 취향 알림 설정")
@RestController
@RequestMapping("/v1/preferences")
@RequiredArgsConstructor
public class PreferenceController {

    private final PreferenceService preferenceService;

    @Operation(summary = "취향 알림 설정 생성 (카드 2~4장, 선택 자연어·카테고리 필터·기준점·반경)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PreferenceResponse create(@AuthenticationPrincipal UUID userId, @Valid @RequestBody PreferenceRequest request) {
        return preferenceService.create(userId, request);
    }

    @GetMapping
    public List<PreferenceResponse> list(@AuthenticationPrincipal UUID userId) {
        return preferenceService.list(userId);
    }

    @PutMapping("/{id}")
    public PreferenceResponse update(@AuthenticationPrincipal UUID userId, @PathVariable UUID id,
                                     @Valid @RequestBody PreferenceRequest request) {
        return preferenceService.update(userId, id, request);
    }

    @Operation(summary = "활성/비활성 전환")
    @PatchMapping("/{id}/active")
    public PreferenceResponse setActive(@AuthenticationPrincipal UUID userId, @PathVariable UUID id,
                                        @RequestBody ActiveRequest request) {
        return preferenceService.setActive(userId, id, request.active());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        preferenceService.delete(userId, id);
    }

    public record ActiveRequest(boolean active) {
    }
}
