package team4.emotionmap.contracts.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DistanceMetersTest {

    @Test
    void identicalPointsHaveZeroDistanceAndDatelineCrossingIsShort() {
        assertThat(DistanceMeters.between(new GeoPoint(12, 34), new GeoPoint(12, 34))).isEqualTo(0.0);

        double dateline = DistanceMeters.between(new GeoPoint(0, 179.9), new GeoPoint(0, -179.9));
        assertThat(dateline).isBetween(20_000.0, 25_000.0);
    }

    @Test
    void polesAndAntipodesRemainFiniteWithinSphericalBounds() {
        double poleToPole = DistanceMeters.between(new GeoPoint(90, 0), new GeoPoint(-90, 0));
        double antipodal = DistanceMeters.between(new GeoPoint(0, 0), new GeoPoint(0, 180));

        assertThat(poleToPole).isFinite().isBetween(20_000_000.0, 20_050_000.0);
        assertThat(antipodal).isFinite().isBetween(20_000_000.0, 20_050_000.0);
        assertThat(antipodal).isEqualTo(Math.PI * DistanceMeters.MEAN_EARTH_RADIUS_METRES);
    }

    @Test
    void resultIsUnroundedForThresholdComparisons() {
        double distance = DistanceMeters.between(new GeoPoint(0, 0), new GeoPoint(0, 0.01));
        double rounded = Math.rint(distance);

        assertThat(distance).isNotEqualTo(rounded);
    }

    @Test
    void coordinatesRejectNonFiniteValuesOnBothAxes() {
        assertThatThrownBy(() -> new GeoPoint(Double.NaN, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(Double.POSITIVE_INFINITY, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(Double.NEGATIVE_INFINITY, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(0, Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(0, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(0, Double.NEGATIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
