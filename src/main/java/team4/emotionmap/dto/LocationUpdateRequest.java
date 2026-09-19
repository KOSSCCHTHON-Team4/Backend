package team4.emotionmap.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 홈 위치 수정 요청 (PATCH /users/me/location).
 */
public record LocationUpdateRequest(
        @NotNull Double homeLat,
        @NotNull Double homeLng
) {
}
