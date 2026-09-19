package team4.emotionmap.contracts.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Preference tie-break evaluation. A successful response contains ordered rank groups: every ID
 * in a group has the same rank and the first group is the set of winner candidates.
 *
 * <p>The result itself has no authority to choose one candidate. Before use, consumers MUST call
 * {@link #validateFor(TieBreakRequest)}. A valid result yields its joint first-place group; an
 * upstream failure and an invalid ranking remain distinct so the consumer can fall back to the
 * original highest-score candidate set without silently selecting an ID.</p>
 */
public record TieBreakResult(
        boolean failed,
        List<List<UUID>> rankGroups,
        AnalysisProvenance provenance,
        String failureReason) {

    public TieBreakResult {
        Objects.requireNonNull(provenance, "provenance");
        rankGroups = immutableGroups(rankGroups);
        if (failed) {
            if (!rankGroups.isEmpty()) {
                throw new IllegalArgumentException("failed result carries no rank groups");
            }
            if (failureReason == null || failureReason.isBlank()) {
                throw new IllegalArgumentException("failed result needs a failure reason");
            }
        } else if (failureReason != null) {
            throw new IllegalArgumentException("successful result carries no failure reason");
        }
    }

    public static TieBreakResult failed(AnalysisProvenance provenance, String reason) {
        return new TieBreakResult(true, List.of(), provenance, reason);
    }

    public static TieBreakResult ranked(List<List<UUID>> rankGroups, AnalysisProvenance provenance) {
        return new TieBreakResult(false, rankGroups, provenance, null);
    }

    /**
     * Checks that successful rank groups form an exact partition of this request's highest-score
     * candidates. The returned {@link Valid#topRankCandidates()} remains a joint winner set;
     * selecting one member (including random selection) belongs to the consumer.
     */
    public Validation validateFor(TieBreakRequest request) {
        Objects.requireNonNull(request, "request");
        if (failed) {
            return new Failure(failureReason);
        }
        if (rankGroups.isEmpty()) {
            return new Invalid("RANK_GROUPS_EMPTY");
        }

        Set<UUID> requestedIds = new HashSet<>();
        for (TieBreakRequest.Candidate candidate : request.candidates()) {
            requestedIds.add(candidate.memoryId());
        }

        Set<UUID> rankedIds = new HashSet<>();
        for (List<UUID> group : rankGroups) {
            if (group == null) {
                return new Invalid("RANK_GROUP_NULL");
            }
            if (group.isEmpty()) {
                return new Invalid("RANK_GROUP_EMPTY");
            }
            for (UUID memoryId : group) {
                if (memoryId == null) {
                    return new Invalid("RANKED_MEMORY_ID_NULL");
                }
                if (!requestedIds.contains(memoryId)) {
                    return new Invalid("RANKED_MEMORY_ID_EXTRANEOUS");
                }
                if (!rankedIds.add(memoryId)) {
                    return new Invalid("RANKED_MEMORY_ID_DUPLICATE");
                }
            }
        }
        if (!rankedIds.equals(requestedIds)) {
            return new Invalid("RANKED_MEMORY_ID_MISSING");
        }
        return new Valid(rankGroups.getFirst());
    }

    private static List<List<UUID>> immutableGroups(List<List<UUID>> rankGroups) {
        if (rankGroups == null) {
            return List.of();
        }
        List<List<UUID>> copiedGroups = new ArrayList<>(rankGroups.size());
        for (List<UUID> group : rankGroups) {
            copiedGroups.add(group == null ? null : Collections.unmodifiableList(new ArrayList<>(group)));
        }
        return Collections.unmodifiableList(copiedGroups);
    }

    public sealed interface Validation permits Valid, Failure, Invalid {}

    /** A complete, valid ranking whose first group contains every equally ranked winner. */
    public record Valid(List<UUID> topRankCandidates) implements Validation {
        public Valid {
            topRankCandidates = List.copyOf(topRankCandidates);
        }
    }

    /** The upstream adapter did not produce a ranking and supplied its safe failure reason. */
    public record Failure(String reason) implements Validation {}

    /** The upstream adapter produced a ranking that violates the request-partition contract. */
    public record Invalid(String reason) implements Validation {}
}
