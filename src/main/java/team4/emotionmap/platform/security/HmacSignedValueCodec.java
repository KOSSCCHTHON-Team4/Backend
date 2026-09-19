package team4.emotionmap.platform.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.signing.SignedClaims;
import team4.emotionmap.contracts.signing.SignedValueCodec;
import team4.emotionmap.contracts.signing.SignedValuePurpose;
import team4.emotionmap.contracts.validation.StrictValues;
import team4.emotionmap.platform.web.json.StrictJson;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link SignedValueCodec} 의 HMAC-SHA256 구현. 형식: {@code base64url(payloadJson) + "." + base64url(mac)}.
 *
 * <p>용도 격리: 키를 {@code HMAC(secret, purpose)} 로 파생하므로 ANALYSIS_TOKEN 키로 만든 값은
 * PAGE_CURSOR 검증에서 서명 자체가 맞지 않는다. 추가로 페이로드의 purpose/subject/version 을 기대값과 비교한다.
 * JWT(인증 토큰)는 다른 라이브러리·다른 비밀을 쓰므로 여기 값이 인증에 쓰일 수 없다.
 */
@Component
public class HmacSignedValueCodec implements SignedValueCodec {

    private static final String MAC_ALGORITHM = "HmacSHA256";
    private static final int FORMAT_VERSION = 1;
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final JsonMapper JSON = StrictJson.mapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP = new TypeReference<>() { };

    private final byte[] rootSecret;

    @org.springframework.beans.factory.annotation.Autowired
    public HmacSignedValueCodec(SigningProperties properties) {
        this(properties.secret());
    }

    HmacSignedValueCodec(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.signing.secret must be at least 32 bytes");
        }
        this.rootSecret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String sign(SignedClaims claims) {
        Objects.requireNonNull(claims, "claims");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("f", FORMAT_VERSION);
        payload.put("p", claims.purpose().name());
        payload.put("s", claims.subjectUserId().toString());
        payload.put("v", claims.payloadVersion());
        payload.put("e", claims.expiresAt().getEpochSecond());
        payload.put("c", claims.claims());
        byte[] body = JSON.writeValueAsBytes(payload);
        String encodedBody = B64.encodeToString(body);
        String mac = B64.encodeToString(mac(claims.purpose(), encodedBody.getBytes(StandardCharsets.US_ASCII)));
        return encodedBody + "." + mac;
    }

    @Override
    public SignedClaims verify(String token, SignedValuePurpose expectedPurpose, UUID expectedUserId,
                               int expectedVersion, Instant now) {
        Objects.requireNonNull(expectedPurpose, "expectedPurpose");
        Objects.requireNonNull(expectedUserId, "expectedUserId");
        Objects.requireNonNull(now, "now");
        ErrorCode invalid = invalidCodeFor(expectedPurpose);

        if (token == null || token.length() > 8192) {
            throw ContractError.of(invalid);
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1 || token.indexOf('.', dot + 1) >= 0) {
            throw ContractError.of(invalid);
        }
        String encodedBody = token.substring(0, dot);
        byte[] presentedMac;
        byte[] body;
        try {
            presentedMac = B64D.decode(token.substring(dot + 1));
            body = B64D.decode(encodedBody);
        } catch (IllegalArgumentException e) {
            throw ContractError.of(invalid);
        }
        // 기대 용도의 파생 키로만 검증한다 → 다른 용도로 만든 값은 여기서 실패한다.
        byte[] expectedMac = mac(expectedPurpose, encodedBody.getBytes(StandardCharsets.US_ASCII));
        if (!MessageDigest.isEqual(expectedMac, presentedMac)) {
            throw ContractError.of(invalid);
        }

        Map<String, Object> payload;
        try {
            payload = JSON.readValue(body, MAP);
        } catch (RuntimeException e) {
            throw ContractError.of(invalid);
        }
        SignedClaims claims = toClaims(payload, invalid);
        if (claims.purpose() != expectedPurpose
                || !claims.subjectUserId().equals(expectedUserId)
                || claims.payloadVersion() != expectedVersion) {
            throw ContractError.of(invalid);
        }
        if (claims.isExpired(now)) {
            throw ContractError.of(expiredCodeFor(expectedPurpose));
        }
        return claims;
    }

    private static SignedClaims toClaims(Map<String, Object> payload, ErrorCode invalid) {
        try {
            if (!(payload.get("f") instanceof Integer f) || f != FORMAT_VERSION) {
                throw ContractError.of(invalid);
            }
            SignedValuePurpose purpose = SignedValuePurpose.valueOf((String) payload.get("p"));
            String subject = (String) payload.get("s");
            if (!StrictValues.isUuid(subject)) {
                throw ContractError.of(invalid);
            }
            int version = (Integer) payload.get("v");
            long expiresEpoch = ((Number) payload.get("e")).longValue();
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = (Map<String, Object>) payload.getOrDefault("c", Map.of());
            Map<String, String> claims = new LinkedHashMap<>();
            raw.forEach((k, v) -> claims.put(k, (String) v));
            return new SignedClaims(purpose, UUID.fromString(subject), version,
                    Instant.ofEpochSecond(expiresEpoch), claims);
        } catch (ContractError e) {
            throw e;
        } catch (RuntimeException e) {
            throw ContractError.of(invalid);
        }
    }

    private byte[] mac(SignedValuePurpose purpose, byte[] data) {
        try {
            Mac root = Mac.getInstance(MAC_ALGORITHM);
            root.init(new SecretKeySpec(rootSecret, MAC_ALGORITHM));
            byte[] purposeKey = root.doFinal(("emotionmap/" + purpose.name()).getBytes(StandardCharsets.UTF_8));
            Mac mac = Mac.getInstance(MAC_ALGORITHM);
            mac.init(new SecretKeySpec(purposeKey, MAC_ALGORITHM));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    static ErrorCode invalidCodeFor(SignedValuePurpose purpose) {
        return switch (purpose) {
            case ANALYSIS_TOKEN -> ErrorCode.ANALYSIS_TOKEN_INVALID;
            case PAGE_CURSOR -> ErrorCode.INVALID_CURSOR;
        };
    }

    static ErrorCode expiredCodeFor(SignedValuePurpose purpose) {
        return switch (purpose) {
            case ANALYSIS_TOKEN -> ErrorCode.ANALYSIS_TOKEN_EXPIRED;
            case PAGE_CURSOR -> ErrorCode.INVALID_CURSOR;
        };
    }
}
