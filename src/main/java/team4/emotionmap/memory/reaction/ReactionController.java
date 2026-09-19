package team4.emotionmap.memory.reaction;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import team4.emotionmap.memory.reaction.dto.ReactionResponse;

/** 기억에 대한 반응 API. 반응은 기억 모듈의 하위 기능이다. */
@RestController
@RequestMapping("/memories/{id}/reactions")
@RequiredArgsConstructor
public class ReactionController {

    private final ReactionService reactionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReactionResponse react(@AuthenticationPrincipal Long userId,
                                  @PathVariable Long id) {
        return reactionService.add(userId, id);
    }
}
