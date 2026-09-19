package team4.emotionmap.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.signing.SignedClaims;
import team4.emotionmap.contracts.signing.SignedValuePurpose;

class HmacSignedValueCodecTest {

    private static final String SECRET = "unit-test-signing-secret-0123456789-abcdefghij";
    private final HmacSignedValueCodec codec = new HmacSignedValueCodec(SECRET);
    private final UUID user = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-09-19T02:00:00Z");
    private final SignedClaims claims = new SignedClaims(SignedValuePurpose.ANALYSIS_TOKEN, user, 1,
            now.plusSeconds(900), Map.of("h", "abc", "a", "--+?"));

    @Test
    void roundTrip() {
        String token = codec.sign(claims);
        SignedClaims verified = codec.verify(token, SignedValuePurpose.ANALYSIS_TOKEN, user, 1, now);
        assertThat(verified).isEqualTo(claims);
        assertThat(token).doesNotContain(user.toString());   // base64url 이라 평문 노출은 없지만 비밀은 아님
    }

    @Test
    void tamperedPayloadOrSignatureIsInvalid() {
        String token = codec.sign(claims);
        String flipped = token.charAt(3) == 'A' ? "B" + token.substring(1) : token.substring(0, 3) + "A" + token.substring(4);
        assertThat(catchThrowableOfType(ContractError.class,
                () -> codec.verify(flipped, SignedValuePurpose.ANALYSIS_TOKEN, user, 1, now)).code())
                .isEqualTo(ErrorCode.ANALYSIS_TOKEN_INVALID);
        String badSig = token.substring(0, token.lastIndexOf('.') + 1) + "AAAA";
        assertThat(catchThrowableOfType(ContractError.class,
                () -> codec.verify(badSig, SignedValuePurpose.ANALYSIS_TOKEN, user, 1, now)).code())
                .isEqualTo(ErrorCode.ANALYSIS_TOKEN_INVALID);
        assertThat(catchThrowableOfType(ContractError.class,
                () -> codec.verify("garbage", SignedValuePurpose.ANALYSIS_TOKEN, user, 1, now)).code())
                .isEqualTo(ErrorCode.ANALYSIS_TOKEN_INVALID);
    }

    @Test
    void purposesAreNotInterchangeable() {
        String analysis = codec.sign(claims);
        assertThat(catchThrowableOfType(ContractError.class,
                () -> codec.verify(analysis, SignedValuePurpose.PAGE_CURSOR, user, 1, now)).code())
                .isEqualTo(ErrorCode.INVALID_CURSOR);
        String cursor = codec.sign(new SignedClaims(SignedValuePurpose.PAGE_CURSOR, user, 1, now.plusSeconds(60), Map.of()));
        assertThat(catchThrowableOfType(ContractError.class,
                () -> codec.verify(cursor, SignedValuePurpose.ANALYSIS_TOKEN, user, 1, now)).code())
                .isEqualTo(ErrorCode.ANALYSIS_TOKEN_INVALID);
    }

    @Test
    void otherUserOtherVersionOtherSecretAreInvalid() {
        String token = codec.sign(claims);
        assertThat(catchThrowableOfType(ContractError.class,
                () -> codec.verify(token, SignedValuePurpose.ANALYSIS_TOKEN, UUID.randomUUID(), 1, now)).code())
                .isEqualTo(ErrorCode.ANALYSIS_TOKEN_INVALID);
        assertThat(catchThrowableOfType(ContractError.class,
                () -> codec.verify(token, SignedValuePurpose.ANALYSIS_TOKEN, user, 2, now)).code())
                .isEqualTo(ErrorCode.ANALYSIS_TOKEN_INVALID);
        HmacSignedValueCodec other = new HmacSignedValueCodec("another-secret-another-secret-another-secret");
        assertThat(catchThrowableOfType(ContractError.class,
                () -> other.verify(token, SignedValuePurpose.ANALYSIS_TOKEN, user, 1, now)).code())
                .isEqualTo(ErrorCode.ANALYSIS_TOKEN_INVALID);
    }

    @Test
    void expiryIsDistinctFromInvalid() {
        String token = codec.sign(claims);
        assertThat(catchThrowableOfType(ContractError.class,
                () -> codec.verify(token, SignedValuePurpose.ANALYSIS_TOKEN, user, 1, now.plusSeconds(900))).code())
                .isEqualTo(ErrorCode.ANALYSIS_TOKEN_EXPIRED);
        String cursor = codec.sign(new SignedClaims(SignedValuePurpose.PAGE_CURSOR, user, 1, now.plusSeconds(1), Map.of()));
        assertThat(catchThrowableOfType(ContractError.class,
                () -> codec.verify(cursor, SignedValuePurpose.PAGE_CURSOR, user, 1, now.plusSeconds(5))).code())
                .isEqualTo(ErrorCode.INVALID_CURSOR);
    }

    @Test
    void shortSecretIsRefused() {
        assertThatThrownBy(() -> new HmacSignedValueCodec("short")).isInstanceOf(IllegalStateException.class);
    }
}
