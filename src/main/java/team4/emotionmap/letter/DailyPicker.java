package team4.emotionmap.letter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import team4.emotionmap.contracts.ai.PreferenceTieBreakPort;
import team4.emotionmap.contracts.ai.TieBreakRequest;
import team4.emotionmap.contracts.ai.TieBreakResult;
import team4.emotionmap.contracts.dictionary.AtmosphereAxis;
import team4.emotionmap.contracts.dictionary.Atmospheres;

/**
 * 일일 편지 한 편 고르기(MVP_PLAN 6.1·6.3·6.4). DB·시간과 무관한 순수 계산이라 단위 테스트로 검증한다.
 *
 * <ol>
 *   <li>점수: 4축 동일 가중치 {@code Σ(1 - |취향 - 편지| / 2)}, 0~4. ±1 값이면 기존 '일치 축 수'와 같다.</li>
 *   <li>최고점 후보만 남긴다(하한 점수 없음). 1개면 바로 선정.</li>
 *   <li>동률이고 자연어 취향이 있으면 그 동률 후보만 AI 로 비교한다. 결과는 점수에 더하지 않는다.</li>
 *   <li>설명 없음·AI 실패·무효 결과·남은 동률은 {@code random_seed} 기반 무작위(재현 가능). 낮은 점수로 내려가지 않는다.</li>
 * </ol>
 * 카테고리는 제외·가산에 쓰지 않는다.
 */
@Slf4j
final class DailyPicker {

    /** 부동소수 합산 오차로 같은 점수가 갈라지지 않게 하는 허용치. */
    static final double TIE_EPSILON = 1e-9;

    record Candidate(UUID memoryId, String content, Atmospheres atmospheres) {
        Candidate {
            Objects.requireNonNull(memoryId, "memoryId");
            Objects.requireNonNull(content, "content");
            Objects.requireNonNull(atmospheres, "atmospheres");
        }
    }

    record Pick(UUID memoryId, int candidateCount, int topTieCount, double fixedScore, TieBreakMethod method) {
    }

    private final PreferenceTieBreakPort tieBreak;
    private final Duration tieBreakTimeout;

    DailyPicker(PreferenceTieBreakPort tieBreak, Duration tieBreakTimeout) {
        this.tieBreak = Objects.requireNonNull(tieBreak, "tieBreak");
        this.tieBreakTimeout = Objects.requireNonNull(tieBreakTimeout, "tieBreakTimeout");
    }

    static double score(Atmospheres preference, Atmospheres letter) {
        double sum = 0.0;
        for (AtmosphereAxis axis : AtmosphereAxis.ordered()) {
            sum += 1.0 - Math.abs(preference.get(axis) - letter.get(axis)) / 2.0;
        }
        double clamped = Math.max(0.0, Math.min(4.0, sum));
        return clamped == 0.0 ? 0.0 : clamped; // canonical +0
    }

    /** 후보가 없으면 empty(= NO_CANDIDATE). 후보 목록은 반경·시간·수신 이력 필터를 이미 통과한 것이어야 한다. */
    Optional<Pick> pick(Atmospheres preference, String description, List<Candidate> candidates, UUID randomSeed) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        double best = Double.NEGATIVE_INFINITY;
        List<Double> scores = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates) {
            double s = score(preference, candidate.atmospheres());
            scores.add(s);
            best = Math.max(best, s);
        }
        List<Candidate> top = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            if (scores.get(i) >= best - TIE_EPSILON) {
                top.add(candidates.get(i));
            }
        }
        top.sort(Comparator.comparing(Candidate::memoryId)); // 무작위 재현을 위해 순서 고정
        int total = candidates.size();
        int ties = top.size();

        if (ties == 1) {
            return Optional.of(new Pick(top.getFirst().memoryId(), total, ties, best, TieBreakMethod.SINGLE_TOP_SCORE));
        }
        if (description == null || description.isBlank()) {
            return Optional.of(random(top, randomSeed, total, ties, best, TieBreakMethod.RANDOM_NO_DESCRIPTION));
        }

        TieBreakRequest request = new TieBreakRequest(description,
                top.stream().map(c -> new TieBreakRequest.Candidate(c.memoryId(), c.content())).toList(),
                tieBreakTimeout);
        TieBreakResult.Validation validation;
        try {
            validation = tieBreak.rank(request).validateFor(request);
        } catch (RuntimeException e) {
            log.warn("daily tie-break adapter failure: {}", e.getClass().getSimpleName());
            return Optional.of(random(top, randomSeed, total, ties, best, TieBreakMethod.RANDOM_MODEL_ERROR));
        }
        return Optional.of(switch (validation) {
            case TieBreakResult.Valid valid -> {
                Set<UUID> winners = new HashSet<>(valid.topRankCandidates());
                List<Candidate> first = top.stream().filter(c -> winners.contains(c.memoryId())).toList();
                yield first.size() == 1
                        ? new Pick(first.getFirst().memoryId(), total, ties, best, TieBreakMethod.NATURAL_LANGUAGE)
                        : random(first, randomSeed, total, ties, best, TieBreakMethod.RANDOM_FINAL_TIE);
            }
            case TieBreakResult.Failure failure ->
                    random(top, randomSeed, total, ties, best, TieBreakMethod.RANDOM_MODEL_ERROR);
            case TieBreakResult.Invalid invalid ->
                    random(top, randomSeed, total, ties, best, TieBreakMethod.RANDOM_INVALID_RESULT);
        });
    }

    private static Pick random(List<Candidate> pool, UUID seed, int total, int ties, double best, TieBreakMethod method) {
        SplittableRandom rng = new SplittableRandom(seed.getMostSignificantBits() ^ seed.getLeastSignificantBits());
        Candidate chosen = pool.get(rng.nextInt(pool.size()));
        return new Pick(chosen.memoryId(), total, ties, best, method);
    }
}
