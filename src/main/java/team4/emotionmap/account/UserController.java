package team4.emotionmap.account;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team4.emotionmap.account.dto.OnboardingRequest;
import team4.emotionmap.account.dto.PreferencesRequest;
import team4.emotionmap.account.dto.UserResponse;

/** Authenticated account profile, onboarding, and mutable mailbox preferences. */
@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserResponse getMe(@AuthenticationPrincipal UUID userId) {
        return userService.getMe(userId);
    }

    @PostMapping("/me/onboarding")
    public UserResponse completeOnboarding(@AuthenticationPrincipal UUID userId,
                                           @Valid @RequestBody OnboardingRequest request) {
        return userService.completeOnboarding(userId, request);
    }

    @PatchMapping("/me/preferences")
    public UserResponse updatePreferences(@AuthenticationPrincipal UUID userId,
                                          @Valid @RequestBody PreferencesRequest request) {
        return userService.updatePreferences(userId, request);
    }
}
