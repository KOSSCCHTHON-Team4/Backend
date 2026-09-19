package team4.emotionmap.account.dto;

/**
 * 로그인 응답. 토큰 발급 방식(JWT 등)은 추후 확정 — 지금은 골격.
 */
public record LoginResponse(
        Long userId,
        String token
) {
}
