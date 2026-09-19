package team4.emotionmap.memory.reaction.dto;

import java.time.OffsetDateTime;
import team4.emotionmap.memory.reaction.Reaction;

/**
 * 반응 응답 DTO.
 */
public record ReactionResponse(
        Long id,
        Long userId,
        Long memoryId,
        OffsetDateTime createdAt
) {
    public static ReactionResponse from(Reaction r) {
        return new ReactionResponse(r.getId(), r.getUserId(), r.getMemoryId(), r.getCreatedAt());
    }
}
