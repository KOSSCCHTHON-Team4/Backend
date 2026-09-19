package team4.emotionmap.platform.security;

import java.util.UUID;

/** Account-owned implementation checks current access and returns the granted role. */
public interface AccountAccessGuard {
    String requireAccess(UUID userId, String requestPath);
}
