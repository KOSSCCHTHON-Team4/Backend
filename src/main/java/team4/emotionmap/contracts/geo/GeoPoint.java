package team4.emotionmap.contracts.geo;

/**
 * 위경도(API_SPEC 9장 {@code Coordinates}). lat -90~90, lng -180~180 만 허용한다.
 * {@link DistanceMeters} 는 이 검증된 좌표로 미터 단위 구면거리를 계산한다.
 */
public record GeoPoint(double lat, double lng) {

    public GeoPoint {
        if (!isValidLat(lat) || !isValidLng(lng)) {
            throw new IllegalArgumentException("coordinates out of range");
        }
    }

    public static boolean isValidLat(double lat) {
        return Double.isFinite(lat) && lat >= -90.0 && lat <= 90.0;
    }

    public static boolean isValidLng(double lng) {
        return Double.isFinite(lng) && lng >= -180.0 && lng <= 180.0;
    }
}
