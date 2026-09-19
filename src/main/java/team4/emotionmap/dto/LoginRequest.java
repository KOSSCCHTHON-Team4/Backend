package team4.emotionmap.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청. (인증 방식은 추후 확정 — 지금은 골격)
 */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
