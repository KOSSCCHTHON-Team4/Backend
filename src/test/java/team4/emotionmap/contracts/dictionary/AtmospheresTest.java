package team4.emotionmap.contracts.dictionary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AtmospheresTest {

    @Test
    void axesAreExactlyFourInSpecOrderWithSpecLabels() {
        assertThat(AtmosphereAxis.ordered()).containsExactly(
                AtmosphereAxis.CROWD_LEVEL, AtmosphereAxis.SPATIAL_FEEL,
                AtmosphereAxis.COMPANY_FIT, AtmosphereAxis.STAY_STYLE);
        assertThat(AtmosphereAxis.CROWD_LEVEL.negativeLabel()).isEqualTo("조용한");
        assertThat(AtmosphereAxis.CROWD_LEVEL.positiveLabel()).isEqualTo("북적이는");
        assertThat(AtmosphereAxis.SPATIAL_FEEL.negativeLabel()).isEqualTo("아늑한");
        assertThat(AtmosphereAxis.SPATIAL_FEEL.positiveLabel()).isEqualTo("탁 트인");
        assertThat(AtmosphereAxis.COMPANY_FIT.negativeLabel()).isEqualTo("혼자 가기 좋은");
        assertThat(AtmosphereAxis.COMPANY_FIT.positiveLabel()).isEqualTo("함께 가기 좋은");
        assertThat(AtmosphereAxis.STAY_STYLE.negativeLabel()).isEqualTo("오래 머물기 좋은");
        assertThat(AtmosphereAxis.STAY_STYLE.positiveLabel()).isEqualTo("잠깐 들르기 좋은");
        assertThat(AtmosphereAxis.DEFINITION_VERSION).isEqualTo(1);
        assertThat(AtmosphereAxis.STAY_STYLE.columnName()).isEqualTo("stay_style");
    }

    @Test
    void finalAtmospheresRejectZeroAndOtherValues() {
        assertThatThrownBy(() -> new Atmospheres(0, 1, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Atmospheres(2, 1, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Atmospheres(-1, -2, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        Atmospheres ok = new Atmospheres(-1, 1, -1, 1);
        assertThat(ok.get(AtmosphereAxis.STAY_STYLE)).isEqualTo(1);
        assertThat(ok.toMap()).hasSize(4);
    }

    @Test
    void analyzedAllowsNullButNeverZero() {
        AnalyzedAtmospheres partial = new AnalyzedAtmospheres(-1, -1, 1, null);
        assertThat(partial.knownCount()).isEqualTo(3);
        assertThat(partial.isComplete()).isFalse();
        assertThat(partial.toComplete()).isEmpty();
        assertThat(new AnalyzedAtmospheres(-1, -1, 1, 1).toComplete()).contains(new Atmospheres(-1, -1, 1, 1));
        assertThatThrownBy(() -> new AnalyzedAtmospheres(0, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(AnalyzedAtmospheres.ALL_UNKNOWN.isEmpty()).isTrue();
    }
}
