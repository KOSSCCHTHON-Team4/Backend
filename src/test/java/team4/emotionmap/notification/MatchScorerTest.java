package team4.emotionmap.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import team4.emotionmap.contracts.config.MatchingProperties;
import team4.emotionmap.contracts.dictionary.VibeVector;

/** 기획 §7 1차 판정 공식·구간. */
class MatchScorerTest {

    private final MatchScorer scorer = new MatchScorer(MatchingProperties.defaults());

    @Test
    void identicalDirectionIsNotify() {
        VibeVector u = new VibeVector(-1, 0, -1, 0);
        VibeVector p = new VibeVector(-0.5, -0.1, -0.5, 0.1); // 코사인 ≈ 0.96 → s ≈ 0.98
        MatchScorer.Stage1 r = scorer.evaluate(u, p, 200, 1000);
        assertThat(r.similarity()).isGreaterThanOrEqualTo(0.85);
        assertThat(r.decision()).isEqualTo(MatchScorer.Decision.NOTIFY);
        assertThat(r.score()).isCloseTo(0.85 * r.similarity() + 0.15 * 0.8, within(1e-9));
    }

    @Test
    void partialOverlapGoesToVerify() {
        VibeVector u = new VibeVector(-1, 0, -1, 0);
        VibeVector p = new VibeVector(-0.4, 0.4, 0.0, 0.4);   // cos = 0.4/(√2·√0.48) ≈ 0.408 → s ≈ 0.70
        MatchScorer.Stage1 r = scorer.evaluate(u, p, 0, 1000);
        assertThat(r.similarity()).isBetween(0.65, 0.85);
        assertThat(r.decision()).isEqualTo(MatchScorer.Decision.VERIFY);
    }

    @Test
    void oppositeOrZeroIsDrop() {
        VibeVector u = new VibeVector(-1, 0, -1, 0);
        assertThat(scorer.evaluate(u, new VibeVector(1, 0, 1, 0), 0, 1000).decision()).isEqualTo(MatchScorer.Decision.DROP);
        assertThat(scorer.evaluate(u, VibeVector.ZERO, 0, 1000).similarity()).isEqualTo(0.0);
        assertThat(scorer.distanceScore(1500, 1000)).isEqualTo(0.0);
        assertThat(scorer.distanceScore(0, 1000)).isEqualTo(1.0);
    }
}
