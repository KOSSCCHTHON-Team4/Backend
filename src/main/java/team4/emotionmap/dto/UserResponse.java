package team4.emotionmap.dto;

import java.time.OffsetDateTime;
import team4.emotionmap.domain.User;

/**
 * 사용자 응답 DTO.
 */
public record UserResponse(
        Long id,
        String nickname,
        String email,
        Double homeLat,
        Double homeLng,
        OffsetDateTime createdAt
) {
    public static UserResponse from(User u) {
        return new UserResponse(
                u.getId(), u.getNickname(), u.getEmail(),
                u.getHomeLat(), u.getHomeLng(), u.getCreatedAt());
    }
}
