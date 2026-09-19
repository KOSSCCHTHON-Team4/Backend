package team4.emotionmap.contracts.dictionary;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * AI 분석 결과의 4축(API_SPEC 9장 {@code AnalyzedAtmospheres}). 축 값은 -1/+1 또는 <b>근거 없음 = null</b>.
 * 최종 저장에는 쓸 수 없고 {@link #toComplete()} 로 {@link Atmospheres} 가 될 때만 저장 가능하다.
 * 계정 취향·사진으로 null 축을 채우지 않는다(불변 규칙 1).
 */
public record AnalyzedAtmospheres(Integer crowdLevel, Integer spatialFeel, Integer companyFit, Integer stayStyle) {

    public static final AnalyzedAtmospheres ALL_UNKNOWN = new AnalyzedAtmospheres(null, null, null, null);

    public AnalyzedAtmospheres {
        check(crowdLevel);
        check(spatialFeel);
        check(companyFit);
        check(stayStyle);
    }

    private static void check(Integer value) {
        if (value != null) {
            AtmosphereAxis.requireValidValue(value);
        }
    }

    public Integer get(AtmosphereAxis axis) {
        return switch (axis) {
            case CROWD_LEVEL -> crowdLevel;
            case SPATIAL_FEEL -> spatialFeel;
            case COMPANY_FIT -> companyFit;
            case STAY_STYLE -> stayStyle;
        };
    }

    public Map<AtmosphereAxis, Integer> toMap() {
        Map<AtmosphereAxis, Integer> map = new EnumMap<>(AtmosphereAxis.class);
        for (AtmosphereAxis axis : AtmosphereAxis.values()) {
            map.put(axis, get(axis));
        }
        return map;
    }

    public int knownCount() {
        int n = 0;
        for (AtmosphereAxis axis : AtmosphereAxis.values()) {
            if (get(axis) != null) {
                n++;
            }
        }
        return n;
    }

    public boolean isComplete() {
        return knownCount() == AtmosphereAxis.values().length;
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
