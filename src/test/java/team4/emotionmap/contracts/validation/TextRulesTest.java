package team4.emotionmap.contracts.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import org.junit.jupiter.api.Test;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.error.FieldError;

class TextRulesTest {

    @Test
    void contentIsNeverNormalizedButBlankIsRejected() {
        String raw = "  첫 줄\r\n둘째 줄  ";
        assertThat(TextRules.requireContent(raw, 3000, "content", ErrorCode.VALIDATION_ERROR)).isSameAs(raw);
        ContractError blank = catchThrowableOfType(ContractError.class,
                () -> TextRules.requireContent(" \n\t ", 3000, "content", ErrorCode.VALIDATION_ERROR));
        assertThat(blank.fieldErrors()).containsExactly(FieldError.required("content"));
    }

    @Test
    void lengthIsCodePointsNotUtf16Units() {
        String emoji = "😀😀😀"; // 3 code points, 6 UTF-16 units
        assertThat(TextRules.codePointLength(emoji)).isEqualTo(3);
        assertThat(TextRules.requireContent(emoji, 3, "content", ErrorCode.VALIDATION_ERROR)).isEqualTo(emoji);
        ContractError tooLong = catchThrowableOfType(ContractError.class,
                () -> TextRules.requireContent(emoji, 2, "content", ErrorCode.VALIDATION_ERROR));
        assertThat(tooLong.fieldErrors()).containsExactly(FieldError.tooLong("content"));
    }

    @Test
    void optionalTextIsTrimmedAndBlankBecomesNull() {
        assertThat(TextRules.normalizeOptionalText("  조용한 곳  ", 1000, "d", ErrorCode.VALIDATION_ERROR))
                .isEqualTo("조용한 곳");
        assertThat(TextRules.normalizeOptionalText("   ", 1000, "d", ErrorCode.VALIDATION_ERROR)).isNull();
        assertThat(TextRules.normalizeOptionalText(null, 1000, "d", ErrorCode.VALIDATION_ERROR)).isNull();
    }

    @Test
    void passwordIsUntouched() {
        String pw = "  Pa ss ";
        assertThat(TextRules.requirePassword(pw, "password")).isSameAs(pw);
        assertThat(catchThrowableOfType(ContractError.class, () -> TextRules.requirePassword("", "password")).code())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(catchThrowableOfType(ContractError.class, () -> TextRules.requirePassword(null, "password")).code())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }
}
