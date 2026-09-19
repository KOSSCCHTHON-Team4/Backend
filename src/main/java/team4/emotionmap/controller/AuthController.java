package team4.emotionmap.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import team4.emotionmap.dto.LoginRequest;
import team4.emotionmap.dto.LoginResponse;
import team4.emotionmap.dto.SignupRequest;
import team4.emotionmap.dto.UserResponse;
import team4.emotionmap.service.AuthService;

/**
 * 인증 API — 공개 엔드포인트(회원가입/로그인만 permitAll).
 *   POST /auth/signup -> 회원가입
 *   POST /auth/login  -> 로그인, JWT 발급
 * 그 외 모든 API 는 Authorization: Bearer <token> 헤더가 필요하다.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
