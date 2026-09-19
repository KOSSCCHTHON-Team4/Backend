package team4.emotionmap.contracts.dictionary;

import java.util.EnumMap;
import java.util.Map;

/**
 * 최종 저장·설정용 4축 값(API_SPEC 9장 {@code Atmospheres}). 네 축은 각각 유한한 {@code [-1, 1]}
 * binary64 이며 constructor 에서 signed zero 를 {@code +0.0} 으로 정규화한다.
 *
 * <p>이 타입이 존재한다는 것 자체가 "4축이 완성되었다"는 뜻이다. 미결 축이 있는 AI 분석 결과는
 * {@link AnalyzedAtmospheres} 를 쓴다.
 */
public record Atmospheres(double crowdLevel, double spatialFeel, double companyFit, double stayStyle) {

    public Atmospheres {
        crowdLevel = AtmosphereAxis.requireValidValue(crowdLevel);
        spatialFeel = AtmosphereAxis.requireValidValue(spatialFeel);
        companyFit = AtmosphereAxis.requireValidValue(companyFit);
        stayStyle = AtmosphereAxis.requireValidValue(stayStyle);
    }

    public double get(AtmosphereAxis axis) {
        return switch (axis) {
            case CROWD_LEVEL -> crowdLevel;
            case SPATIAL_FEEL -> spatialFeel;
            case COMPANY_FIT -> companyFit;
            case STAY_STYLE -> stayStyle;
        };
    }

    public static Atmospheres fromMap(Map<AtmosphereAxis, Double> values) {
        for (AtmosphereAxis axis : AtmosphereAxis.ordered()) {
            if (values.get(axis) == null) {
                throw new IllegalArgumentException("missing axis " + axis);
            }
        }
        return new Atmospheres(
                values.get(AtmosphereAxis.CROWD_LEVEL),
                values.get(AtmosphereAxis.SPATIAL_FEEL),
                values.get(AtmosphereAxis.COMPANY_FIT),
                values.get(AtmosphereAxis.STAY_STYLE));
    }

    /** 표시 순서(EnumMap 순서)의 정규화된 축→값. */
    public Map<AtmosphereAxis, Double> toMap() {
        Map<AtmosphereAxis, Double> map = new EnumMap<>(AtmosphereAxis.class);
        for (AtmosphereAxis axis : AtmosphereAxis.ordered()) {
            map.put(axis, get(axis));
        }
        return map;
    }

    public AnalyzedAtmospheres toAnalyzed() {
        return new AnalyzedAtmospheres(crowdLevel, spatialFeel, companyFit, stayStyle);
    }
}
