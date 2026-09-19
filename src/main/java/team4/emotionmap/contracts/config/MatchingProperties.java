package team4.emotionmap.contracts.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 기획 §5~§7 매칭·집계 수치. 기본값은 기획 문서의 값이며 {@code app.matching.*} 로 바꿀 수 있다.
 *
 * @param radiusMeters                 후보 필터 기본 반경(기획: 1km)
 * @param fallbackRadiusMeters         기본 반경 안 장소가 {@code minPlacesForPrimaryRadius} 미만이면 쓰는 반경(3km)
 * @param minPlacesForPrimaryRadius    위 전환 기준 장소 수(3)
 * @param notifyThreshold              s ≥ 이 값 → 즉시 알림(0.85)
 * @param verifyThreshold              verifyThreshold ≤ s &lt; notifyThreshold → 2차 AI 판정(0.65)
 * @param similarityWeight             정렬 점수의 유사도 가중(0.85)
 * @param distanceWeight               정렬 점수의 거리 가중(0.15)
 * @param placeVibeSmoothing           장소 벡터 P = 평균 × n/(n+smoothing) 의 smoothing(2)
 * @param mergeDistanceMeters          미등록 장소 병합 반경(20m)
 * @param categoryConfidenceThreshold  AI 카테고리 신뢰도 하한(0.6). 미만이면 제안을 OTHER 로 바꾸고 사용자가 수정
 * @param reviewsForVerify             2차 판정·문구 생성에 보내는 리뷰 상위 개수(5)
 */
@ConfigurationProperties(prefix = "app.matching")
public record MatchingProperties(
        Integer radiusMeters,
        Integer fallbackRadiusMeters,
        Integer minPlacesForPrimaryRadius,
        Double notifyThreshold,
        Double verifyThreshold,
        Double similarityWeight,
        Double distanceWeight,
        Integer placeVibeSmoothing,
        Integer mergeDistanceMeters,
        Double categoryConfidenceThreshold,
        Integer reviewsForVerify
) {
    public MatchingProperties {
        radiusMeters = orDefault(radiusMeters, 1000);
        fallbackRadiusMeters = orDefault(fallbackRadiusMeters, 3000);
        minPlacesForPrimaryRadius = orDefault(minPlacesForPrimaryRadius, 3);
        notifyThreshold = orDefault(notifyThreshold, 0.85);
        verifyThreshold = orDefault(verifyThreshold, 0.65);
        similarityWeight = orDefault(similarityWeight, 0.85);
        distanceWeight = orDefault(distanceWeight, 0.15);
        placeVibeSmoothing = orDefault(placeVibeSmoothing, 2);
        mergeDistanceMeters = orDefault(mergeDistanceMeters, 20);
        categoryConfidenceThreshold = orDefault(categoryConfidenceThreshold, 0.6);
        reviewsForVerify = orDefault(reviewsForVerify, 5);
        if (radiusMeters < 1 || fallbackRadiusMeters < radiusMeters || minPlacesForPrimaryRadius < 1
                || placeVibeSmoothing < 0 || mergeDistanceMeters < 0 || reviewsForVerify < 1) {
            throw new IllegalArgumentException("invalid app.matching values");
        }
        if (!(0 <= verifyThreshold && verifyThreshold <= notifyThreshold && notifyThreshold <= 1)) {
            throw new IllegalArgumentException("thresholds must satisfy 0 <= verify <= notify <= 1");
        }
        if (!(0 <= categoryConfidenceThreshold && categoryConfidenceThreshold <= 1)) {
            throw new IllegalArgumentException("categoryConfidenceThreshold must be within [0, 1]");
        }
    }

    public static MatchingProperties defaults() {
        return new MatchingProperties(null, null, null, null, null, null, null, null, null, null, null);
    }

    private static <T> T orDefault(T value, T fallback) {
        return value == null ? fallback : value;
    }
}
