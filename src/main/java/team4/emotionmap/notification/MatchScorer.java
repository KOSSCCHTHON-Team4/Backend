package team4.emotionmap.notification;

import team4.emotionmap.contracts.config.MatchingProperties;
import team4.emotionmap.contracts.dictionary.VibeVector;

/**
 * 기획 §7 1차 판정(순수 계산). 알림 여부는 유사도 s 만으로, 정렬 점수는 거리까지 섞어 정한다.
 * <pre>
 *   s     = (cos(U, P) + 1) / 2                 ∈ [0, 1]
 *   score = wS × s + wD × (1 - d/radius)         ∈ [0, 1]
 *   s ≥ notifyThreshold → NOTIFY, verifyThreshold ≤ s &lt; notifyThreshold → VERIFY, 그 외 DROP
 * </pre>
 */
public final class MatchScorer {

    public enum Decision { NOTIFY, VERIFY, DROP }

    public record Stage1(double similarity, double score, Decision decision) {
    }

    private final MatchingProperties p;

    public MatchScorer(MatchingProperties properties) {
        this.p = properties;
    }

    public double similarity(VibeVector user, VibeVector place) {
        if (user == null || place == null || user.isZero() || place.isZero()) {
            return 0.0;
        }
        return (user.cosine(place) + 1.0) / 2.0;
    }

    public double distanceScore(double distanceMeters, double radiusMeters) {
        if (radiusMeters <= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, 1.0 - distanceMeters / radiusMeters));
    }

    public Stage1 evaluate(VibeVector user, VibeVector place, double distanceMeters, double radiusMeters) {
        double s = similarity(user, place);
        double score = p.similarityWeight() * s + p.distanceWeight() * distanceScore(distanceMeters, radiusMeters);
        score = Math.max(0.0, Math.min(1.0, score));
        Decision decision;
        if (s >= p.notifyThreshold()) {
            decision = Decision.NOTIFY;
        } else if (s >= p.verifyThreshold()) {
            decision = Decision.VERIFY;
        } else {
            decision = Decision.DROP;
        }
        return new Stage1(s, score, decision);
    }
}
