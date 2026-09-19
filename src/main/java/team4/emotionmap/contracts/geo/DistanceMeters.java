package team4.emotionmap.contracts.geo;

import java.util.Objects;

/**
 * Great-circle distance calculations in metres using the haversine formula.
 * The calculation uses the IUGG mean Earth radius of 6,371,008.8 m; callers must compare the
 * unrounded result directly (for example, {@code distance <= radiusMetres}) for inclusion rules.
 */
public final class DistanceMeters {

    public static final double MEAN_EARTH_RADIUS_METRES = 6_371_008.8;

    private DistanceMeters() {}

    /**
     * Returns the finite, non-negative great-circle distance between two validated coordinates.
     * Longitude subtraction is used directly: haversine's periodic sine term handles dateline crossings.
     */
    public static double between(GeoPoint first, GeoPoint second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");

        double latitudeDelta = Math.toRadians(second.lat() - first.lat());
        double longitudeDelta = Math.toRadians(second.lng() - first.lng());
        double firstLatitude = Math.toRadians(first.lat());
        double secondLatitude = Math.toRadians(second.lat());

        double sinLatitude = Math.sin(latitudeDelta / 2.0);
        double sinLongitude = Math.sin(longitudeDelta / 2.0);
        double haversine = sinLatitude * sinLatitude
                + Math.cos(firstLatitude) * Math.cos(secondLatitude) * sinLongitude * sinLongitude;
        double clampedHaversine = Math.max(0.0, Math.min(1.0, haversine));
        return 2.0 * MEAN_EARTH_RADIUS_METRES * Math.asin(Math.sqrt(clampedHaversine));
    }
}
