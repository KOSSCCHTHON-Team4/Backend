package team4.emotionmap.contracts.dictionary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.List;
import org.junit.jupiter.api.Test;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.error.FieldError;
import team4.emotionmap.contracts.error.FieldErrorReason;

/** A13: 카테고리 4개·중복·허용 밖 코드 → 422 INVALID_CATEGORIES. */
class CategorySelectionTest {

    @Test
    void zeroToThreeDistinctKnownCodesPass() {
        assertThat(CategorySelection.validate("categoryCodes", null)).isEmpty();
        assertThat(CategorySelection.validate("categoryCodes", List.of())).isEmpty();
        assertThat(CategorySelection.validate("categoryCodes", List.of("CAFE", "STUDY_WORK", "OTHER")))
                .containsExactly(PlaceCategoryCode.CAFE, PlaceCategoryCode.STUDY_WORK, PlaceCategoryCode.OTHER);
    }

    @Test
    void fourCodesAreOutOfRange() {
        ContractError e = catchThrowableOfType(ContractError.class,
                () -> CategorySelection.validate("categoryCodes", List.of("CAFE", "BAR", "CULTURE", "SHOPPING")));
        assertThat(e.code()).isEqualTo(ErrorCode.INVALID_CATEGORIES);
        assertThat(e.httpStatus()).isEqualTo(422);
        assertThat(e.fieldErrors()).contains(new FieldError("categoryCodes", FieldErrorReason.OUT_OF_RANGE));
    }

    @Test
    void duplicatesAndUnknownCodesAreReportedPerIndex() {
        ContractError e = catchThrowableOfType(ContractError.class,
                () -> CategorySelection.validate("categoryCodes", List.of("CAFE", "CAFE", "NAVER_12345")));
        assertThat(e.fieldErrors()).containsExactlyInAnyOrder(
                new FieldError("categoryCodes[1]", FieldErrorReason.DUPLICATE),
                new FieldError("categoryCodes[2]", FieldErrorReason.INVALID_VALUE));
    }

    @Test
    void requireValidGuardsPortValues() {
        assertThatThrownBy(() -> CategorySelection.requireValid(
                List.of(PlaceCategoryCode.CAFE, PlaceCategoryCode.CAFE)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
