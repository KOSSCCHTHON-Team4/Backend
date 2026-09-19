package team4.emotionmap.contracts.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import team4.emotionmap.contracts.dictionary.AnalyzedAtmospheres;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;
import team4.emotionmap.contracts.memory.AtmosphereAnalysisStatus;
import team4.emotionmap.contracts.memory.CategoryAnalysisStatus;

class AnalysisResultTest {

    private static final AnalysisProvenance P = new AnalysisProvenance("m", "v1", 1, 1);

    @Test
    void statusesDeriveFromValues() {
        AnalysisResult full = AnalysisResult.of(new AnalyzedAtmospheres(-1, -1, 1, 1), List.of(PlaceCategoryCode.CAFE), P);
        assertThat(full.atmosphereStatus()).isEqualTo(AtmosphereAnalysisStatus.SUCCEEDED);
        assertThat(full.categoryStatus()).isEqualTo(CategoryAnalysisStatus.SUCCEEDED);

        AnalysisResult partial = AnalysisResult.of(new AnalyzedAtmospheres(-1, -1, 1, null), List.of(), P);
        assertThat(partial.atmosphereStatus()).isEqualTo(AtmosphereAnalysisStatus.PARTIAL);
        assertThat(partial.categoryStatus()).isEqualTo(CategoryAnalysisStatus.INSUFFICIENT);

        AnalysisResult failed = AnalysisResult.failed(P, "TIMEOUT");
        assertThat(failed.isUpstreamFailure()).isTrue();
        assertThat(failed.atmospheres().isEmpty()).isTrue();
        assertThat(failed.categories()).isEmpty();
    }

    @Test
    void failureNeverCarriesCategoriesAndNeverMapsToOther() {
        assertThatThrownBy(() -> new AnalysisResult(AnalyzedAtmospheres.ALL_UNKNOWN, List.of(PlaceCategoryCode.OTHER),
                CategoryAnalysisStatus.FAILED, AtmosphereAnalysisStatus.FAILED, P, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AnalysisResult(AnalyzedAtmospheres.ALL_UNKNOWN, List.of(),
                CategoryAnalysisStatus.NOT_RUN, AtmosphereAnalysisStatus.FAILED, P, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AnalysisResult(new AnalyzedAtmospheres(1, 1, 1, 1), List.of(),
                CategoryAnalysisStatus.INSUFFICIENT, AtmosphereAnalysisStatus.PARTIAL, P, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void moderationApprovedHasNoReasons() {
        assertThatThrownBy(() -> new ModerationResult(ModerationVerdict.APPROVED, List.of("x"), P))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ModerationVerdict.ERROR.toStatus().allowsDelivery()).isFalse();
        assertThat(ModerationVerdict.APPROVED.toStatus().allowsDelivery()).isTrue();
    }
}
