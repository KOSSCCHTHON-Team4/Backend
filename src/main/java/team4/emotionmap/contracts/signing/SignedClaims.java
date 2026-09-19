package team4.emotionmap.contracts.signing;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 서명 대상 클레임. 용도·사용자·페이로드 버전·만료가 항상 들어가고, 나머지는 문자열 클레임이다.
 * 본문 원문·이메일·다른 사용자 ID 같은 값은 클레임에 넣지 않는다(해시만).
 */
public record SignedClaims(
        SignedValuePurpose purpose,
        UUID subjectUserId,
        int payloadVersion,
        Instant expiresAt,
        Map<String, String> claims
) {
    public SignedClaims {
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(subjectUserId, "subjectUserId");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (payloadVersion < 1) {
            throw new IllegalArgumentException("payloadVersion starts at 1");
        }
        Map<String, String> sorted = new TreeMap<>();
        if (claims != null) {
            claims.forEach((k, v) -> {
                if (k == null || v == null || k.isBlank()) {
                    throw new IllegalArgumentException("claim keys/values must be non-null");
                }
                sorted.put(k, v);
            });
        }
        claims = Map.copyOf(sorted);
    }

    public String claim(String key) {
        return claims.get(key);
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
