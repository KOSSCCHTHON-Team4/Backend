package team4.emotionmap.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team4.emotionmap.dto.LocationUpdateRequest;
import team4.emotionmap.dto.UserResponse;
import team4.emotionmap.service.UserService;

/**
 * 사용자 API. 인증 필요(JWT).
 * 현재 사용자 ID 는 인증 컨텍스트에서 얻는다(@AuthenticationPrincipal Long userId).
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserResponse getMe(@AuthenticationPrincipal Long userId) {
        return userService.getMe(userId);
    }

    @PatchMapping("/me/location")
    public UserResponse updateLocation(@AuthenticationPrincipal Long userId,
                                       @Valid @RequestBody LocationUpdateRequest request) {
        return userService.updateLocation(userId, request);
    }
}
