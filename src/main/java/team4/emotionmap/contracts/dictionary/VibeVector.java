package team4.emotionmap.contracts.dictionary;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

/**
 * 4축 분위기 벡터(기획 §3·§6·§7). 성분 순서는 {@link AtmosphereAxis#ordered()} 와 같고 각 값은 [-1, 1].
 * 경험·취향의 canonical 축은 모두 연속 binary64 이며, 장소 평균과 코사인 계산은 기존 공식을 그대로 쓴다.
 */
public record VibeVector(double crowdLevel, double spatialFeel, double companyFit, double stayStyle) {

    public static final VibeVector ZERO = new VibeVector(0, 0, 0, 0);

    public VibeVector {
        check(crowdLevel);
        check(spatialFeel);
        check(companyFit);
        check(stayStyle);
    }

    private static void check(double v) {
        if (Double.isNaN(v) || v < -1.0 || v > 1.0) {
            throw new IllegalArgumentException("vibe component must be within [-1, 1]");
        }
    }

    public static VibeVector of(Atmospheres a) {
        return new VibeVector(a.crowdLevel(), a.spatialFeel(), a.companyFit(), a.stayStyle());
    }

    public double[] toArray() {
        return new double[]{crowdLevel, spatialFeel, companyFit, stayStyle};
    }

    public double get(AtmosphereAxis axis) {
        return toArray()[axis.order() - 1];
    }

    // JavaBean 관례상 isZero()는 Jackson에 "zero" 프로퍼티로 보여 PlaceResponse.vibe 직렬화에
    // 의도치 않은 필드를 얹힌다(API_SPEC에 없는 값). 4개 축 성분 외에는 아무것도 내보내지 않는다.
    @JsonIgnore
    public boolean isZero() {
        return crowdLevel == 0 && spatialFeel == 0 && companyFit == 0 && stayStyle == 0;
    }

    public double norm() {
        return Math.sqrt(crowdLevel * crowdLevel + spatialFeel * spatialFeel
                + companyFit * companyFit + stayStyle * stayStyle);
    }

    /** 코사인 유사도 [-1, 1]. 어느 한쪽이 영벡터면 0(비교 불가). */
    public double cosine(VibeVector other) {
        double denominator = norm() * other.norm();
        if (denominator == 0.0) {
            return 0.0;
        }
        double dot = crowdLevel * other.crowdLevel + spatialFeel * other.spatialFeel
                + companyFit * other.companyFit + stayStyle * other.stayStyle;
        return Math.max(-1.0, Math.min(1.0, dot / denominator));
    }

    /**
     * 장소 벡터 P = 평균 × n/(n+smoothing) (기획 §6). 리뷰가 적을수록 0 쪽으로 약하게 반영된다.
     * 빈 목록이면 ZERO.
     */
    public static VibeVector shrunkMean(List<VibeVector> samples, int smoothing) {
        if (samples == null || samples.isEmpty()) {
            return ZERO;
        }
        if (smoothing < 0) {
            throw new IllegalArgumentException("smoothing must be >= 0");
        }
        double[] sum = new double[4];
        for (VibeVector s : samples) {
            double[] a = s.toArray();
            for (int i = 0; i < 4; i++) {
                sum[i] += a[i];
            }
        }
        int n = samples.size();
        double factor = (double) n / (n + smoothing);
        return new VibeVector(sum[0] / n * factor, sum[1] / n * factor, sum[2] / n * factor, sum[3] / n * factor);
    }
}
