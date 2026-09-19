package team4.emotionmap.contracts.dictionary;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * AI 분석 결과의 4축(API_SPEC 9장 {@code AnalyzedAtmospheres}). {@code null} 만 미결이며, 0 을 포함한
 * 유한 {@code [-1, 1]} 값은 모두 알려진 정상 값이다. nonnull 값은 signed zero 를 {@code +0.0} 으로
 * 정규화한다.
 *
 * <p>최종 저장에는 쓸 수 없고 {@link #toComplete()} 로 {@link Atmospheres} 가 될 때만 저장 가능하다.
 * 계정 취향·사진으로 null 축을 채우지 않는다(불변 규칙 1).
 */
public record AnalyzedAtmospheres(Double crowdLevel, Double spatialFeel, Double companyFit, Double stayStyle) {

    public static final AnalyzedAtmospheres ALL_UNKNOWN = new AnalyzedAtmospheres(null, null, null, null);

    public AnalyzedAtmospheres {
        crowdLevel = normalized(crowdLevel);
        spatialFeel = normalized(spatialFeel);
        companyFit = normalized(companyFit);
        stayStyle = normalized(stayStyle);
    }

    private static Double normalized(Double value) {
        if (value == null) {
            return null;
        }
        double normalized = AtmosphereAxis.requireValidValue(value);
        return Double.doubleToLongBits(value) == Double.doubleToLongBits(normalized)
                ? value : Double.valueOf(normalized);
    }

    public Double get(AtmosphereAxis axis) {
        return switch (axis) {
            case CROWD_LEVEL -> crowdLevel;
            case SPATIAL_FEEL -> spatialFeel;
            case COMPANY_FIT -> companyFit;
            case STAY_STYLE -> stayStyle;
        };
    }

    public Map<AtmosphereAxis, Double> toMap() {
        Map<AtmosphereAxis, Double> map = new EnumMap<>(AtmosphereAxis.class);
        for (AtmosphereAxis axis : AtmosphereAxis.ordered()) {
            map.put(axis, get(axis));
        }
        return map;
    }

    public int knownCount() {
        int n = 0;
        for (AtmosphereAxis axis : AtmosphereAxis.ordered()) {
            if (get(axis) != null) {
                n++;
            }
        }
        return n;
    }

    public boolean isComplete() {
        return knownCount() == AtmosphereAxis.ordered().size();
    }

    public boolean isEmpty() {
        return knownCount() == 0;
    }

    /** 네 축이 모두 있을 때만 완성 값을 돌려준다. */
    public Optional<Atmospheres> toComplete() {
        if (!isComplete()) {
            return Optional.empty();
        }
        return Optional.of(new Atmospheres(crowdLevel, spatialFeel, companyFit, stayStyle));
    }
}
