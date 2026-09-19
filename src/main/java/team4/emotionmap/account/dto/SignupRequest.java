package team4.emotionmap.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청 (POST /auth/signup) — 공개 엔드포인트.
 */
public record SignupRequest(
        @NotBlank @Size(max = 50) String nickname,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password
) {
}
