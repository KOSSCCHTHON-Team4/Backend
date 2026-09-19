package team4.emotionmap.platform.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import team4.emotionmap.contracts.account.UserContext;
import team4.emotionmap.contracts.validation.StrictValues;

/**
 * {@link UserContext} 구현: SecurityContext 의 principal 을 UUID 로 읽는다.
 * principal 이 UUID 이거나 엄격한 UUID 문자열일 때만 인증으로 인정한다.
 * UUID 가 아닌 principal 은 인증으로 취급하지 않는다.
 */
@Component
public class SecurityContextUserContext implements UserContext {

    @Override
    public Optional<UUID> currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UUID uuid) {
            return Optional.of(uuid);
        }
        if (principal instanceof String s && StrictValues.isUuid(s)) {
            return Optional.of(UUID.fromString(s));
        }
        return Optional.empty();
    }
}
