package team4.emotionmap.letter;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team4.emotionmap.letter.dto.LetterLikeResponse;
import team4.emotionmap.letter.dto.LetterResponse;

@RestController
@RequestMapping("/v1/letters")
@RequiredArgsConstructor
public class LetterController {

    private final LetterService letterService;

    @GetMapping
    public List<LetterResponse> myLetters(@AuthenticationPrincipal UUID userId) {
        return letterService.findForReceiver(userId);
    }

    @PatchMapping("/{deliveryId}/read")
    public LetterResponse markRead(@AuthenticationPrincipal UUID userId, @PathVariable UUID deliveryId) {
        return letterService.markRead(userId, deliveryId);
    }

    @PostMapping("/{deliveryId}/like")
    public LetterLikeResponse like(@AuthenticationPrincipal UUID userId, @PathVariable UUID deliveryId) {
        return letterService.like(userId, deliveryId);
    }
}
