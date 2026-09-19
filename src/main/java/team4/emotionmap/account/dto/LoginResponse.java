package team4.emotionmap.account.dto;

import java.time.Instant;
import java.util.UUID;

public record LoginResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        long expiresInSeconds,
        boolean refreshSupported,
        LoginUser user
) {
    public record LoginUser(UUID id, String email, boolean hasOnboarded) { }
}
