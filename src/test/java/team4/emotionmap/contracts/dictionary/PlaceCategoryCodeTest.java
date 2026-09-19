package team4.emotionmap.contracts.dictionary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlaceCategoryCodeTest {

    @Test
    void eightCodesInSpecOrder() {
        assertThat(PlaceCategoryCode.ordered()).extracting(Enum::name).containsExactly(
                "CAFE", "RESTAURANT", "BAR", "PARK_WALK", "CULTURE", "STUDY_WORK", "SHOPPING", "OTHER");
        for (int i = 0; i < 8; i++) {
            assertThat(PlaceCategoryCode.ordered().get(i).order()).isEqualTo(i + 1);
            assertThat(PlaceCategoryCode.ordered().get(i).id()).isEqualTo(i + 1);
        }
        assertThat(PlaceCategoryCode.PARK_WALK.label()).isEqualTo("공원·산책");
        assertThat(PlaceCategoryCode.STUDY_WORK.label()).isEqualTo("공부·작업 공간");
        assertThat(PlaceCategoryCode.TAXONOMY_VERSION).isEqualTo(1);
        assertThat(PlaceCategoryCode.MAX_PER_MEMORY).isEqualTo(3);
    }

    @Test
    void fromCodeIsExactMatchOnly() {
        assertThat(PlaceCategoryCode.fromCode("CAFE")).contains(PlaceCategoryCode.CAFE);
        assertThat(PlaceCategoryCode.fromCode("cafe")).isEmpty();
        assertThat(PlaceCategoryCode.fromCode(" CAFE")).isEmpty();
        assertThat(PlaceCategoryCode.fromCode(null)).isEmpty();
    }
}
