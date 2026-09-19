package team4.emotionmap.contracts.dictionary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class VibeVectorTest {

    @Test
    void cosineAndBounds() {
        VibeVector u = new VibeVector(-1, 0, -1, 0);
        assertThat(u.cosine(new VibeVector(-1, -1, -1, -1))).isCloseTo(Math.sqrt(2) / 2, within(1e-9));
        assertThat(u.cosine(new VibeVector(1, 0, 1, 0))).isCloseTo(-1.0, within(1e-9));
        assertThat(u.cosine(VibeVector.ZERO)).isEqualTo(0.0);
        assertThatThrownBy(() -> new VibeVector(1.2, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shrunkMeanFollowsPlanFormula() {
        // n=2 리뷰 모두 (-1,-1,-1,-1) → 평균 -1 × 2/(2+2) = -0.5
        VibeVector p = VibeVector.shrunkMean(List.of(new VibeVector(-1, -1, -1, -1), new VibeVector(-1, -1, -1, -1)), 2);
        assertThat(p.crowdLevel()).isCloseTo(-0.5, within(1e-9));
        // n=1 → -1 × 1/3
        VibeVector single = VibeVector.shrunkMean(List.of(new VibeVector(-1, 1, -1, 1)), 2);
        assertThat(single.spatialFeel()).isCloseTo(1.0 / 3, within(1e-9));
        assertThat(VibeVector.shrunkMean(List.of(), 2)).isEqualTo(VibeVector.ZERO);
        // 방향은 보존된다: 실수 벡터와 ±1 벡터의 코사인은 1
        assertThat(p.cosine(new VibeVector(-1, -1, -1, -1))).isCloseTo(1.0, within(1e-9));
    }
}
