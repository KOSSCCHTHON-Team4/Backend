package team4.emotionmap.account;

import jakarta.validation.Validator;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import team4.emotionmap.account.dto.LoginRequest;

/** Shared, non-persistent input rules for email-password credentials. */
final class EmailPasswordInput {

    static final int MAX_PASSWORD_UTF8_BYTES = 72;

    private static final byte[] LOGIN_ATTEMPT_PREFIX =
            "emotionmap:login-attempt:v1:".getBytes(StandardCharsets.UTF_8);
    private static final HexFormat LOWERCASE_HEX = HexFormat.of();

    private EmailPasswordInput() {
    }

    static String normalizeEmail(String email, Validator validator) {
        if (email == null || !hasWellFormedUtf16(email)) {
            throw new IllegalArgumentException("email is invalid");
        }
        String normalized = EmailPasswordCredential.normalizeEmailLookupKey(email);
        if (!hasWellFormedUtf16(normalized)
                || !validator.validateValue(LoginRequest.class, "email", normalized).isEmpty()) {
            throw new IllegalArgumentException("email is invalid");
        }
        return normalized;
    }

    static boolean hasValidPassword(CharSequence password) {
        if (password == null || password.isEmpty()) {
            return false;
        }
        int bytes = 0;
        for (int index = 0; index < password.length(); index++) {
            char character = password.charAt(index);
            if (character <= 0x7f) {
                bytes++;
            } else if (character <= 0x7ff) {
                bytes += 2;
            } else if (Character.isHighSurrogate(character)) {
                if (index + 1 >= password.length() || !Character.isLowSurrogate(password.charAt(index + 1))) {
                    return false;
                }
                bytes += 4;
                index++;
            } else if (Character.isLowSurrogate(character)) {
                return false;
            } else {
                bytes += 3;
            }
            if (bytes > MAX_PASSWORD_UTF8_BYTES) {
                return false;
            }
        }
        return true;
    }

    static String loginAttemptKey(String normalizedEmail) {
        ByteBuffer normalizedEmailBytes = encodeUtf8Strict(normalizedEmail);
        byte[] digest = null;
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            sha256.update(LOGIN_ATTEMPT_PREFIX);
            sha256.update(normalizedEmailBytes);
            digest = sha256.digest();
            return LOWERCASE_HEX.formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        } finally {
            if (normalizedEmailBytes.hasArray()) {
                Arrays.fill(normalizedEmailBytes.array(), (byte) 0);
            }
            if (digest != null) {
                Arrays.fill(digest, (byte) 0);
            }
        }
    }

    private static boolean hasWellFormedUtf16(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return false;
                }
                index++;
            } else if (Character.isLowSurrogate(character)) {
                return false;
            }
        }
        return true;
    }

    private static ByteBuffer encodeUtf8Strict(String value) {
        try {
            return StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("email is invalid", e);
        }
    }

}
