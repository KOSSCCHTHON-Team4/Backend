package team4.emotionmap.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;
import team4.emotionmap.contracts.dictionary.VibeVector;

/**
 * 경험 한 건의 4축은 ±1 정수(저장 규칙)지만, 장소 프로필 벡터(Place.vibe*)는 실수다(기획 §6, VibeVector 헤더 주석).
 * -1..1 전 구간(경계·소수 포함)이 엔티티를 온전히 왕복하는지 확인한다.
 */
class PlaceTest {

    private static final double[] SWEEP = {
        -1.0, -0.999999, -0.87654321, -0.5, -0.333333, -0.1, -0.0001, 0.0,
        0.0001, 0.1, 0.333333, 0.5, 0.87654321, 0.999999, 1.0,
    };

    private static Place place() {
        return Place.builder().lat(37.5).lng(127.0).build();
    }

    @Test
    void updateProfileRoundTripsFullRealRangeWithoutTruncation() {
        Place place = place();
        Instant at = Instant.parse("2026-09-20T00:00:00Z");
        for (double v : SWEEP) {
            VibeVector vibe = new VibeVector(v, -v, v / 2, -v / 2);
            place.updateProfile(vibe, 7, PlaceCategoryCode.CAFE, at);

            assertThat(place.vibe()).as("v=%s", v).isEqualTo(vibe);
            assertThat(place.getVibeCrowd()).as("v=%s", v).isEqualTo(v);
            assertThat(place.getVibeSpatial()).as("v=%s", v).isEqualTo(-v);
            assertThat(place.getVibeCompany()).as("v=%s", v).isEqualTo(v / 2);
            assertThat(place.getVibeStay()).as("v=%s", v).isEqualTo(-v / 2);
        }
    }

    @Test
    void noReviewsClearsVibeToNull() {
        Place place = place();
        place.updateProfile(new VibeVector(0.5, -0.5, 0.5, -0.5), 3, null, Instant.now());
        assertThat(place.vibe()).isNotNull();

        place.updateProfile(null, 0, null, Instant.now());
        assertThat(place.vibe()).isNull();
        assertThat(place.getVibeCrowd()).isNull();
        assertThat(place.getReviewCount()).isZero();
    }
}
