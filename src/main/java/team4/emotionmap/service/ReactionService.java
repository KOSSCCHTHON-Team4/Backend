package team4.emotionmap.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team4.emotionmap.domain.Reaction;
import team4.emotionmap.dto.ReactionResponse;
import team4.emotionmap.repository.ReactionRepository;

@Service
@RequiredArgsConstructor
public class ReactionService {

    private final ReactionRepository reactionRepository;

    /** Memory 에 반응 추가. (user, memory) 중복은 DB UNIQUE 로 방지되지만 사전 체크도 한다. */
    @Transactional
    public ReactionResponse add(Long userId, Long memoryId) {
        if (reactionRepository.existsByUserIdAndMemoryId(userId, memoryId)) {
            throw new IllegalStateException("이미 반응한 기억입니다.");
        }
        Reaction saved = reactionRepository.save(
                Reaction.builder().userId(userId).memoryId(memoryId).build());
        return ReactionResponse.from(saved);
    }
}
