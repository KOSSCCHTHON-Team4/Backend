package team4.emotionmap.contracts.geo;

/**
 * 위경도(API_SPEC 9장 {@code Coordinates}). lat -90~90, lng -180~180 만 허용.
 * 거리 계산은 BE2 의 {@code DistancePolicy} 가 담당하며 여기에는 계산을 두지 않는다.
 */
public record GeoPoint(double lat, double lng) {

    public GeoPoint {
        if (!isValidLat(lat) || !isValidLng(lng)) {
            throw new IllegalArgumentException("coordinates out of range");
        }
    }

    public static boolean isValidLat(double lat) {
        return !Double.isNaN(lat) && lat >= -90.0 && lat <= 90.0;
    }

    public static boolean isValidLng(double lng) {
        return !Double.isNaN(lng) && lng >= -180.0 && lng <= 180.0;
    }
}
