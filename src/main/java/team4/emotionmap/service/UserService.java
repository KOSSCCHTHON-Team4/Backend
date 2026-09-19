package team4.emotionmap.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team4.emotionmap.domain.User;
import team4.emotionmap.dto.LocationUpdateRequest;
import team4.emotionmap.dto.UserResponse;
import team4.emotionmap.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));
        return UserResponse.from(user);
    }

    /** 홈 위치 수정. */
    @Transactional
    public UserResponse updateLocation(Long userId, LocationUpdateRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));
        user.updateHomeLocation(req.homeLat(), req.homeLng());
        return UserResponse.from(user);
    }
}
