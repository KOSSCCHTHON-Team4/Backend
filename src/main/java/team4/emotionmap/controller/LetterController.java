package team4.emotionmap.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team4.emotionmap.dto.LetterResponse;
import team4.emotionmap.service.LetterService;

/**
 * 편지(추천 배달) API. 인증 필요(JWT).
 *   GET   /letters             -> 현재 사용자에게 온 추천 편지 목록
 *   PATCH /letters/{id}/read   -> 읽음 처리
 * 현재 사용자 ID 는 인증 컨텍스트에서 얻는다.
 */
@RestController
@RequestMapping("/letters")
@RequiredArgsConstructor
public class LetterController {

    private final LetterService letterService;

    @GetMapping
    public List<LetterResponse> myLetters(@AuthenticationPrincipal Long userId) {
        return letterService.findForReceiver(userId);
    }

    @PatchMapping("/{id}/read")
    public LetterResponse markRead(@PathVariable Long id) {
        return letterService.markRead(id);
    }
}
