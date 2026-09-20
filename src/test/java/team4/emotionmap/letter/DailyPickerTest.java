package team4.emotionmap.letter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import team4.emotionmap.contracts.ai.AnalysisProvenance;
import team4.emotionmap.contracts.ai.PreferenceTieBreakPort;
import team4.emotionmap.contracts.ai.TieBreakRequest;
import team4.emotionmap.contracts.ai.TieBreakResult;
import team4.emotionmap.contracts.dictionary.Atmospheres;

class DailyPickerTest {

    private static final AnalysisProvenance PROV = new AnalysisProvenance("t", "t", 2, 1);
    private static final Atmospheres QUIET_ALONE = new Atmospheres(-1, -1, -1, -1);
    private static final UUID SEED = UUID.fromString("00000000-0000-0000-0000-000000000042");

    private final PreferenceTieBreakPort port = mock(PreferenceTieBreakPort.class);
    private final DailyPicker picker = new DailyPicker(port, Duration.ofSeconds(5));

    private static DailyPicker.Candidate letter(String id, double c, double s, double f, double st) {
        return new DailyPicker.Candidate(UUID.fromString("00000000-0000-0000-0000-0000000000" + id), "본문 " + id,
                new Atmospheres(c, s, f, st));
    }

    @Test
    void scoreEqualsMatchedAxisCountAtEndpointsForAll16x16Combinations() {
        List<Atmospheres> all = new ArrayList<>();
        for (int bits = 0; bits < 16; bits++) {
            all.add(new Atmospheres((bits & 1) == 0 ? -1 : 1, (bits & 2) == 0 ? -1 : 1,
                    (bits & 4) == 0 ? -1 : 1, (bits & 8) == 0 ? -1 : 1));
        }
        for (Atmospheres p : all) {
            for (Atmospheres m : all) {
                int matches = (p.crowdLevel() == m.crowdLevel() ? 1 : 0) + (p.spatialFeel() == m.spatialFeel() ? 1 : 0)
                        + (p.companyFit() == m.companyFit() ? 1 : 0) + (p.stayStyle() == m.stayStyle() ? 1 : 0);
                assertThat(DailyPicker.score(p, m)).isEqualTo(matches);
            }
        }
    }

    @Test
    void continuousScoreIsCloserIsHigherAndCanonicalZero() {
        Atmospheres pref = new Atmospheres(-0.6, 0.2, 0, -0.4);
        assertThat(DailyPicker.score(pref, pref)).isEqualTo(4.0);
        assertThat(DailyPicker.score(pref, new Atmospheres(-0.5, 0.2, 0, -0.4)))
                .isGreaterThan(DailyPicker.score(pref, new Atmospheres(0.5, 0.2, 0, -0.4)));
        double zero = DailyPicker.score(new Atmospheres(-1, -1, -1, -1), new Atmospheres(1, 1, 1, 1));
        assertThat(Double.doubleToLongBits(zero)).isEqualTo(Double.doubleToLongBits(0.0));
    }

    @Test
    void noCandidateIsEmpty() {
        assertThat(picker.pick(QUIET_ALONE, "조용한 곳", List.of(), SEED)).isEmpty();
    }

    @Test
    void singleTopScoreWinsWithoutAi() {
        var best = letter("01", -1, -1, -1, -1);
        var pick = picker.pick(QUIET_ALONE, "조용한 곳", List.of(letter("02", 1, 1, 1, 1), best), SEED).orElseThrow();
        assertThat(pick.memoryId()).isEqualTo(best.memoryId());
        assertThat(pick.method()).isEqualTo(TieBreakMethod.SINGLE_TOP_SCORE);
        assertThat(pick.fixedScore()).isEqualTo(4.0);
        assertThat(pick.candidateCount()).isEqualTo(2);
        assertThat(pick.topTieCount()).isEqualTo(1);
        verify(port, never()).rank(any());
    }

    @Test
    void tieWithoutDescriptionIsReproducibleRandomAmongTopOnly() {
        var a = letter("01", -1, -1, -1, 1);
        var b = letter("02", -1, -1, 1, -1);
        var low = letter("03", 1, 1, 1, 1);
        var first = picker.pick(QUIET_ALONE, null, List.of(a, b, low), SEED).orElseThrow();
        var again = picker.pick(QUIET_ALONE, "  ", List.of(low, b, a), SEED).orElseThrow();
        assertThat(first.method()).isEqualTo(TieBreakMethod.RANDOM_NO_DESCRIPTION);
        assertThat(first.memoryId()).isIn(a.memoryId(), b.memoryId()).isEqualTo(again.memoryId());
        assertThat(first.topTieCount()).isEqualTo(2);
        assertThat(first.fixedScore()).isEqualTo(3.0);
        verify(port, never()).rank(any());
    }

    @Test
    void naturalLanguageComparesOnlyTopTiesAndPicksSingleWinner() {
        var a = letter("01", -1, -1, -1, 1);
        var b = letter("02", -1, -1, 1, -1);
        var low = letter("03", 1, 1, 1, 1);
        when(port.rank(any())).thenReturn(TieBreakResult.ranked(List.of(List.of(b.memoryId()), List.of(a.memoryId())), PROV));
        var pick = picker.pick(QUIET_ALONE, "친구랑", List.of(a, b, low), SEED).orElseThrow();
        assertThat(pick.memoryId()).isEqualTo(b.memoryId());
        assertThat(pick.method()).isEqualTo(TieBreakMethod.NATURAL_LANGUAGE);
        ArgumentCaptor<TieBreakRequest> sent = ArgumentCaptor.forClass(TieBreakRequest.class);
        verify(port).rank(sent.capture());
        assertThat(sent.getValue().candidates()).extracting(TieBreakRequest.Candidate::memoryId)
                .containsExactlyInAnyOrder(a.memoryId(), b.memoryId());
    }

    @Test
    void jointFirstPlaceIsRandomWithinWinners() {
        var a = letter("01", -1, -1, -1, 1);
        var b = letter("02", -1, -1, 1, -1);
        var c = letter("04", -1, 1, -1, -1);
        when(port.rank(any())).thenReturn(TieBreakResult.ranked(
                List.of(List.of(a.memoryId(), c.memoryId()), List.of(b.memoryId())), PROV));
        var pick = picker.pick(QUIET_ALONE, "창가", List.of(a, b, c), SEED).orElseThrow();
        assertThat(pick.method()).isEqualTo(TieBreakMethod.RANDOM_FINAL_TIE);
        assertThat(pick.memoryId()).isIn(a.memoryId(), c.memoryId());
    }

    @Test
    void aiFailureInvalidAndExceptionFallBackToTopScoreRandom() {
        var a = letter("01", -1, -1, -1, 1);
        var b = letter("02", -1, -1, 1, -1);
        List<DailyPicker.Candidate> pool = List.of(a, b, letter("03", 1, 1, 1, 1));
        Set<UUID> top = new HashSet<>(List.of(a.memoryId(), b.memoryId()));

        when(port.rank(any())).thenReturn(TieBreakResult.failed(PROV, "down"));
        var failed = picker.pick(QUIET_ALONE, "x", pool, SEED).orElseThrow();
        assertThat(failed.method()).isEqualTo(TieBreakMethod.RANDOM_MODEL_ERROR);
        assertThat(top).contains(failed.memoryId());

        when(port.rank(any())).thenReturn(TieBreakResult.ranked(List.of(List.of(UUID.randomUUID())), PROV));
        var invalid = picker.pick(QUIET_ALONE, "x", pool, SEED).orElseThrow();
        assertThat(invalid.method()).isEqualTo(TieBreakMethod.RANDOM_INVALID_RESULT);
        assertThat(top).contains(invalid.memoryId());

        when(port.rank(any())).thenThrow(new IllegalStateException("adapter"));
        var thrown = picker.pick(QUIET_ALONE, "x", pool, SEED).orElseThrow();
        assertThat(thrown.method()).isEqualTo(TieBreakMethod.RANDOM_MODEL_ERROR);
        assertThat(top).contains(thrown.memoryId());
    }
}
