package team4.emotionmap.account.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import team4.emotionmap.account.UserPreferenceVersion;

public record Atmospheres(
        @JsonProperty("CROWD_LEVEL") Short crowdLevel,
        @JsonProperty("SPATIAL_FEEL") Short spatialFeel,
        @JsonProperty("COMPANY_FIT") Short companyFit,
        @JsonProperty("STAY_STYLE") Short stayStyle
) {
    private static final Set<String> AXES = Set.of("CROWD_LEVEL", "SPATIAL_FEEL", "COMPANY_FIT", "STAY_STYLE");

    public Atmospheres {
        requireAxis(crowdLevel);
        requireAxis(spatialFeel);
        requireAxis(companyFit);
        requireAxis(stayStyle);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Atmospheres fromJson(Map<String, Object> values) {
        if (values == null || !values.keySet().equals(AXES)) {
            throw invalid();
        }
        return new Atmospheres(axis(values.get("CROWD_LEVEL")), axis(values.get("SPATIAL_FEEL")),
                axis(values.get("COMPANY_FIT")), axis(values.get("STAY_STYLE")));
    }

    public static Atmospheres from(UserPreferenceVersion version) {
        return new Atmospheres(version.getCrowdLevel(), version.getSpatialFeel(),
                version.getCompanyFit(), version.getStayStyle());
    }

    private static Short axis(Object value) {
        // Do not let JSON decimals or numeric strings silently coerce to smallint.
        if (!(value instanceof Integer number) || (number != -1 && number != 1)) {
            throw invalid();
        }
        return number.shortValue();
    }

    private static void requireAxis(Short value) {
        if (value == null || (value != -1 && value != 1)) {
            throw invalid();
        }
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_ATMOSPHERES");
    }
}
