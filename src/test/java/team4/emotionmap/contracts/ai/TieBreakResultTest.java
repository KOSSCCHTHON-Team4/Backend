package team4.emotionmap.contracts.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TieBreakResultTest {

    private static final AnalysisProvenance PROVENANCE = new AnalysisProvenance("test", "v1", 1, 1);
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    private static TieBreakRequest request() {
        return new TieBreakRequest("preference", List.of(
                new TieBreakRequest.Candidate(A, "a"),
                new TieBreakRequest.Candidate(B, "b"),
                new TieBreakRequest.Candidate(C, "c")), Duration.ofSeconds(1));
    }

    @Test
    void validRankingReturnsCompleteJointTopSubset() {
        TieBreakResult result = TieBreakResult.ranked(List.of(List.of(A, B), List.of(C)), PROVENANCE);

        TieBreakResult.Validation validation = result.validateFor(request());
        assertThat(validation).isInstanceOf(TieBreakResult.Valid.class);
        assertThat(((TieBreakResult.Valid) validation).topRankCandidates()).containsExactly(A, B);
    }

    @Test
    void malformedGroupsAndRequestsAreRejected() {
        assertThatThrownBy(() -> new TieBreakRequest("preference", List.of(
                new TieBreakRequest.Candidate(A, "a"),
                new TieBreakRequest.Candidate(A, "duplicate")), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(TieBreakResult.ranked(List.of(), PROVENANCE).validateFor(request()))
                .isInstanceOf(TieBreakResult.Invalid.class);
        assertThat(TieBreakResult.ranked(Arrays.asList((List<UUID>) null), PROVENANCE).validateFor(request()))
                .isInstanceOf(TieBreakResult.Invalid.class);
        assertThat(TieBreakResult.ranked(List.of(List.of()), PROVENANCE).validateFor(request()))
                .isInstanceOf(TieBreakResult.Invalid.class);
        assertThat(TieBreakResult.ranked(List.of(Arrays.asList(A, (UUID) null), List.of(B, C)), PROVENANCE)
                .validateFor(request())).isInstanceOf(TieBreakResult.Invalid.class);
        assertThat(TieBreakResult.ranked(List.of(List.of(A, A), List.of(B, C)), PROVENANCE).validateFor(request()))
                .isInstanceOf(TieBreakResult.Invalid.class);
        assertThat(TieBreakResult.ranked(List.of(List.of(A), List.of(B)), PROVENANCE).validateFor(request()))
                .isInstanceOf(TieBreakResult.Invalid.class);
        UUID foreign = UUID.randomUUID();
        assertThat(TieBreakResult.ranked(List.of(List.of(A, foreign), List.of(B, C)), PROVENANCE)
                .validateFor(request())).isInstanceOf(TieBreakResult.Invalid.class);

        assertThat(TieBreakResult.failed(PROVENANCE, "UPSTREAM").validateFor(request()))
                .isInstanceOf(TieBreakResult.Failure.class);
    }

    @Test
    void rankGroupsAndNestedListsAreImmutableCopies() {
        List<UUID> first = new ArrayList<>(List.of(A, B));
        List<List<UUID>> groups = new ArrayList<>(List.of(first, List.of(C)));
        TieBreakResult result = TieBreakResult.ranked(groups, PROVENANCE);
        first.add(C);
        groups.clear();

        assertThat(result.rankGroups()).containsExactly(List.of(A, B), List.of(C));
        assertThatThrownBy(() -> result.rankGroups().add(List.of(A)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.rankGroups().getFirst().add(C))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
