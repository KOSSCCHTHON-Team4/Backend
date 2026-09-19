package team4.emotionmap.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team4.emotionmap.domain.User;
import team4.emotionmap.dto.LoginRequest;
import team4.emotionmap.dto.LoginResponse;
import team4.emotionmap.dto.SignupRequest;
import team4.emotionmap.dto.UserResponse;
import team4.emotionmap.repository.UserRepository;
import team4.emotionmap.security.JwtTokenProvider;

/**
 * 인증 서비스. 회원가입/로그인만 담당(공개 엔드포인트).
 * 비밀번호는 BCrypt 해시로 저장하고, 로그인 성공 시 JWT 를 발급한다.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Transactional
    public UserResponse signup(SignupRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }
        if (userRepository.existsByNickname(req.nickname())) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }
        User saved = userRepository.save(User.builder()
                .nickname(req.nickname())
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .build());
        return UserResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다."));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        String token = tokenProvider.createToken(user.getId());
        return new LoginResponse(user.getId(), token);
    }
}
