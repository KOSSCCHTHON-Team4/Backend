package team4.emotionmap.contracts.dictionary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.List;
import org.junit.jupiter.api.Test;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.error.FieldError;

/** 기획 §4: 카드 8장, 2~4장 선택, 같은 축 반대 카드 불가, 안 고른 축 0. */
class PreferenceCardTest {

    @Test
    void eightCardsShareAxisLabels() {
        assertThat(PreferenceCard.values()).hasSize(8);
        assertThat(PreferenceCard.QUIET.label()).isEqualTo("조용한");
        assertThat(PreferenceCard.QUICK_STOP.label()).isEqualTo("잠깐 들르기 좋은");
        assertThat(PreferenceCard.fromLabel("혼자 가기 좋은")).contains(PreferenceCard.SOLO);
        assertThat(PreferenceCard.fromLabel("조용")).isEmpty();
    }

    @Test
    void exampleFromPlanProducesVector() {
        List<PreferenceCard> cards = PreferenceCard.parse("cards", List.of("조용한", "혼자 가기 좋은"));
        assertThat(PreferenceCard.toVector(cards)).isEqualTo(new VibeVector(-1, 0, -1, 0));
    }

    @Test
    void selectionRulesAreEnforced() {
        ContractError tooFew = catchThrowableOfType(ContractError.class,
                () -> PreferenceCard.parse("cards", List.of("조용한")));
        assertThat(tooFew.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(tooFew.fieldErrors()).contains(FieldError.outOfRange("cards"));

        ContractError opposite = catchThrowableOfType(ContractError.class,
                () -> PreferenceCard.parse("cards", List.of("조용한", "북적이는")));
        assertThat(opposite.fieldErrors()).containsExactly(FieldError.invalid("cards[1]"));

        ContractError duplicate = catchThrowableOfType(ContractError.class,
                () -> PreferenceCard.parse("cards", List.of("조용한", "조용한", "아늑한")));
        assertThat(duplicate.fieldErrors()).containsExactly(FieldError.duplicate("cards[1]"));

        ContractError unknown = catchThrowableOfType(ContractError.class,
                () -> PreferenceCard.parse("cards", List.of("조용한", "감성적인")));
        assertThat(unknown.fieldErrors()).containsExactly(FieldError.invalid("cards[1]"));
    }

    @Test
    void fourCardsCoverAllAxes() {
        List<PreferenceCard> cards = PreferenceCard.parse("cards",
                List.of("북적이는", "탁 트인", "함께 가기 좋은", "잠깐 들르기 좋은"));
        assertThat(PreferenceCard.toVector(cards)).isEqualTo(new VibeVector(1, 1, 1, 1));
    }
}
