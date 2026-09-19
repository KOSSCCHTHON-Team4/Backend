package team4.emotionmap.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import team4.emotionmap.account.dto.LoginRequest;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.error.FieldError;
import team4.emotionmap.platform.web.GlobalExceptionHandlerSupport;
import team4.emotionmap.platform.web.json.StrictJson;
import tools.jackson.databind.json.JsonMapper;
class EmailPasswordInputTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;
    private static final JsonMapper JSON = StrictJson.mapper();

    @BeforeAll
    static void openValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void exactUtf8PasswordLimitAcceptsBmpAndSupplementaryCharactersButRejectsTheNextUnit() {
        String bmpPassword = "가".repeat(24); // 24 * 3 UTF-8 bytes
        String supplementaryPassword = "😀".repeat(18); // 18 * 4 UTF-8 bytes

        assertThat(EmailPasswordInput.hasValidPassword(bmpPassword)).isTrue();
        assertThat(EmailPasswordInput.hasValidPassword(bmpPassword + "가")).isFalse();
        assertThat(EmailPasswordInput.hasValidPassword(supplementaryPassword)).isTrue();
        assertThat(EmailPasswordInput.hasValidPassword(supplementaryPassword + "😀")).isFalse();
    }

    @Test
    void malformedSurrogateSequencesAreRejected() {
        assertThat(EmailPasswordInput.hasValidPassword("\uD83D")).isFalse();
        assertThat(EmailPasswordInput.hasValidPassword("\uDE00")).isFalse();
        assertThat(EmailPasswordInput.hasValidPassword("x\uD83Dy")).isFalse();
    }

    @Test
    void nonemptyWhitespacePasswordRemainsValidWithoutTrimming() {
        assertThat(EmailPasswordInput.hasValidPassword(" ")).isTrue();
    }

    @Test
    void emailValidationUsesNormalizedValueAndRejectsMalformedLocalPart() {
        assertThat(EmailPasswordInput.normalizeEmail("  Mixed.Case@Example.COM  ", validator))
                .isEqualTo("mixed.case@example.com");

        assertThatThrownBy(() -> EmailPasswordInput.normalizeEmail("name..part@example.com", validator))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void malformedUnicodeEmailsAreRejectedBeforeNormalizationAndDigestEncoding() {
        assertMalformedEmailRejected("user\uD83D@example.com");
        assertMalformedEmailRejected("user\uDE00@example.com");
        assertMalformedEmailRejected("user\uD83D!\uDE00@example.com");
    }

    @Test
    void wellFormedSupplementaryEmailBytesDoNotAliasToReplacementBytes() {
        String supplementaryEmail = "user\uD83D\uDE00@example.com";
        String replacementEmail = "user\uFFFD@example.com";

        assertThat(EmailPasswordInput.loginAttemptKey(supplementaryEmail))
                .isNotEqualTo(EmailPasswordInput.loginAttemptKey(replacementEmail));
    }

    @Test
    void loginJsonRequiresBothCredentialsAndRejectsNullValuesBeforeValidation() {
        assertRequiredField("{\"password\":\"secret\"}", "email");
        assertRequiredField("{\"email\":\"user@example.com\"}", "password");
        assertRequiredField("{\"email\":null,\"password\":\"secret\"}", "email");
        assertRequiredField("{\"email\":\"user@example.com\",\"password\":null}", "password");
    }

    @Test
    void loginJsonNormalizesEmailButPreservesPasswordExactly() {
        String rawPassword = "  p  ";
        LoginRequest request = JSON.readValue(
                "{\"email\":\"  Mixed.Case@Example.COM  \",\"password\":\"" + rawPassword + "\"}",
                LoginRequest.class);

        assertThat(request.email()).isEqualTo("mixed.case@example.com");
        assertThat(request.password()).isEqualTo(rawPassword);
    }

    private static void assertRequiredField(String json, String field) {
        Throwable failure = catchThrowable(() -> JSON.readValue(json, LoginRequest.class));
        assertThat(failure).as("expected JSON rejection for %s", json).isNotNull();

        ContractError error = GlobalExceptionHandlerSupport.translate(failure);
        assertThat(error.code()).isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(error.fieldErrors()).containsExactly(FieldError.required(field));
    }

    private static void assertMalformedEmailRejected(String email) {
        assertThatThrownBy(() -> EmailPasswordInput.normalizeEmail(email, validator))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EmailPasswordInput.loginAttemptKey(email))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
