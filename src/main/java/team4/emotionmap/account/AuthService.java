package team4.emotionmap.account;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import team4.emotionmap.account.dto.LoginRequest;
import team4.emotionmap.account.dto.LoginResponse;
import team4.emotionmap.platform.security.JwtTokenProvider;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final EmailPasswordCredentialRepository credentialRepository;
    private final UserPreferenceVersionRepository preferenceRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        EmailPasswordCredential credential = credentialRepository.findByEmailLookupKey(
                        EmailPasswordCredential.normalizeEmailLookupKey(request.email()))
                .orElseThrow(AuthService::invalidCredentials);
        if (!passwordEncoder.matches(request.password(), credential.getPasswordHash())) {
            throw invalidCredentials();
        }
        User user = userRepository.findById(credential.getUserId())
                .orElseThrow(AuthService::invalidCredentials);
        AccountAccessService.requireActive(user);
        JwtTokenProvider.IssuedToken token = tokenProvider.createToken(user.getId());
        boolean onboarded = user.getMailboxEnabledAt() != null
                && preferenceRepository.findByUserIdAndRevision(user.getId(), 1L).isPresent();
        return new LoginResponse(token.token(), "Bearer", token.expiresAt(), token.expiresInSeconds(), false,
                new LoginResponse.LoginUser(user.getId(), credential.getEmail(), onboarded));
    }

    private static ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
    }
}
