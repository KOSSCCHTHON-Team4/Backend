package team4.emotionmap.contracts.dictionary;

import java.util.EnumMap;
import java.util.Map;

/**
 * 최종 저장·설정용 4축 값(API_SPEC 9장 {@code Atmospheres}). 네 축 모두 -1/+1 이어야 하며
 * 0·null·소수·문자열은 생성 시점에 거절한다 (JSON 토큰 수준 검사는 platform 의 역직렬화기가 담당).
 *
 * <p>이 타입이 존재한다는 것 자체가 "4축이 완성되었다"는 뜻이다. 미결 축이 있는 AI 분석 결과는
 * {@link AnalyzedAtmospheres} 를 쓴다.
 */
public record Atmospheres(int crowdLevel, int spatialFeel, int companyFit, int stayStyle) {

    public Atmospheres {
        AtmosphereAxis.requireValidValue(crowdLevel);
        AtmosphereAxis.requireValidValue(spatialFeel);
        AtmosphereAxis.requireValidValue(companyFit);
        AtmosphereAxis.requireValidValue(stayStyle);
    }

    public int get(AtmosphereAxis axis) {
        return switch (axis) {
            case CROWD_LEVEL -> crowdLevel;
            case SPATIAL_FEEL -> spatialFeel;
            case COMPANY_FIT -> companyFit;
            case STAY_STYLE -> stayStyle;
        };
    }

    public static Atmospheres fromMap(Map<AtmosphereAxis, Integer> values) {
        for (AtmosphereAxis axis : AtmosphereAxis.values()) {
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

    /** 표시 순서(EnumMap 순서)의 축→값. */
    public Map<AtmosphereAxis, Integer> toMap() {
        Map<AtmosphereAxis, Integer> map = new EnumMap<>(AtmosphereAxis.class);
        for (AtmosphereAxis axis : AtmosphereAxis.values()) {
            map.put(axis, get(axis));
        }
        return map;
    }

    public AnalyzedAtmospheres toAnalyzed() {
        return new AnalyzedAtmospheres(crowdLevel, spatialFeel, companyFit, stayStyle);
    }
}
