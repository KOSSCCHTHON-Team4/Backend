package team4.emotionmap.contracts.fakes;

import java.util.Optional;
import java.util.UUID;
import team4.emotionmap.contracts.account.UserContext;

/** 테스트용 고정 인증 주체. null 이면 미인증. */
public final class FixedUserContext implements UserContext {

    private final UUID userId;

    public FixedUserContext(UUID userId) {
        this.userId = userId;
    }

    public static FixedUserContext anonymous() {
        return new FixedUserContext(null);
    }

    @Override
    public Optional<UUID> currentUserId() {
        return Optional.ofNullable(userId);
    }
}
