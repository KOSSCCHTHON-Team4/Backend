package team4.emotionmap.contracts.memory;

import java.util.Objects;
import team4.emotionmap.contracts.dictionary.AtmosphereAxis;

/** 네 축 각각의 출처({@code crowd_source} 등). 사본은 원문 값을 그대로 유지한다. */
public record AtmosphereSources(AxisSource crowdLevel, AxisSource spatialFeel, AxisSource companyFit, AxisSource stayStyle) {

    public static final AtmosphereSources ALL_USER =
            new AtmosphereSources(AxisSource.USER, AxisSource.USER, AxisSource.USER, AxisSource.USER);

    public AtmosphereSources {
        Objects.requireNonNull(crowdLevel);
        Objects.requireNonNull(spatialFeel);
        Objects.requireNonNull(companyFit);
        Objects.requireNonNull(stayStyle);
    }

    public AxisSource get(AtmosphereAxis axis) {
        return switch (axis) {
            case CROWD_LEVEL -> crowdLevel;
            case SPATIAL_FEEL -> spatialFeel;
            case COMPANY_FIT -> companyFit;
            case STAY_STYLE -> stayStyle;
        };
    }
}
