package team4.emotionmap.contracts.dictionary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AtmospheresTest {

    @Test
    void axesAreExactlyFourInSpecOrderWithEndpointLabels() {
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
        assertThat(AtmosphereAxis.DEFINITION_VERSION).isEqualTo(2);
        assertThat(AtmosphereAxis.CROWD_LEVEL.labelOf(-1.0)).isEqualTo("조용한");
        assertThat(AtmosphereAxis.CROWD_LEVEL.labelOf(1.0)).isEqualTo("북적이는");
        assertThatThrownBy(() -> AtmosphereAxis.CROWD_LEVEL.labelOf(0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(AtmosphereAxis.STAY_STYLE.columnName()).isEqualTo("stay_style");
    }

    @Test
    void finalAtmospheresAcceptContinuumAndNormalizeSignedZero() {
        Atmospheres values = new Atmospheres(-0.0, 0.25, -1.0, 1.0);
        assertThat(values.get(AtmosphereAxis.CROWD_LEVEL)).isEqualTo(0.0);
        assertThat(values.get(AtmosphereAxis.SPATIAL_FEEL)).isEqualTo(0.25);
        assertThat(Double.doubleToLongBits(values.crowdLevel()))
                .isEqualTo(Double.doubleToLongBits(0.0));
        assertThat(values.toMap()).containsEntry(AtmosphereAxis.CROWD_LEVEL, 0.0).hasSize(4);
        assertThat(values.toAnalyzed()).isEqualTo(new AnalyzedAtmospheres(0.0, 0.25, -1.0, 1.0));
    }

    @Test
    void finalAtmospheresRejectNonfiniteAndOutOfRangeValues() {
        assertThatThrownBy(() -> new Atmospheres(Double.NaN, 0.0, 0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Atmospheres(Double.POSITIVE_INFINITY, 0.0, 0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Atmospheres(1.0000000000000002, 0.0, 0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void analyzedAllowsNullAndTreatsZeroAsKnown() {
        AnalyzedAtmospheres partial = new AnalyzedAtmospheres(-1.0, 0.0, 0.125, null);
        assertThat(partial.knownCount()).isEqualTo(3);
        assertThat(partial.isComplete()).isFalse();
        assertThat(partial.toComplete()).isEmpty();
        assertThat(new AnalyzedAtmospheres(-1.0, -1.0, 0.0, 1.0).toComplete())
                .contains(new Atmospheres(-1.0, -1.0, 0.0, 1.0));
        assertThat(Double.doubleToLongBits(new AnalyzedAtmospheres(-0.0, null, null, null).crowdLevel()))
                .isEqualTo(Double.doubleToLongBits(0.0));
        assertThatThrownBy(() -> new AnalyzedAtmospheres(Double.NaN, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(AnalyzedAtmospheres.ALL_UNKNOWN.isEmpty()).isTrue();
    }

    @Test
    void boundedJsonNumberParserRejectsPreRoundingRangeAttacksAndKeepsBinary64Edges() {
        assertThat(AtmosphereAxis.parseJsonNumber("10e-1")).isEqualTo(1.0);
        assertThat(AtmosphereAxis.parseJsonNumber("5e-324")).isEqualTo(Double.MIN_VALUE);
        assertThat(AtmosphereAxis.parseJsonNumber("1e-2147483649")).isEqualTo(0.0);
        assertThat(AtmosphereAxis.parseJsonNumber("0e2147483647")).isEqualTo(0.0);
        assertThat(Double.doubleToLongBits(AtmosphereAxis.parseJsonNumber("-0.0")))
                .isEqualTo(Double.doubleToLongBits(0.0));
        assertThatThrownBy(() -> AtmosphereAxis.parseJsonNumber("1.00000000000000000001"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AtmosphereAxis.parseJsonNumber("-1.00000000000000000001"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AtmosphereAxis.parseJsonNumber("0.10000000000000000001e1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AtmosphereAxis.parseJsonNumber("1e2147483647"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parserRejectsNonJsonAliasesAndOverlongTokens() {
        assertThatThrownBy(() -> AtmosphereAxis.parseJsonNumber(" 1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AtmosphereAxis.parseJsonNumber("+1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AtmosphereAxis.parseJsonNumber("01"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AtmosphereAxis.parseJsonNumber("0." + "0".repeat(999)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
