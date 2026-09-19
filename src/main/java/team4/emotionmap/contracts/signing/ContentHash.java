package team4.emotionmap.contracts.signing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 본문 대응 검증용 해시(SHA-256, hex). 저장·분석·토큰 검증이 <b>같은 원문 문자열</b>에 대해 계산하며
 * 계산 전에 trim/정규화하지 않는다(API_SPEC 2.2). 원문 추적 키로 저장하지 않는다.
 */
public final class ContentHash {

    private ContentHash() {
    }

    public static String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static boolean matches(String content, String expectedHex) {
        return expectedHex != null && MessageDigest.isEqual(
                sha256Hex(content).getBytes(StandardCharsets.US_ASCII),
                expectedHex.getBytes(StandardCharsets.US_ASCII));
    }
}
