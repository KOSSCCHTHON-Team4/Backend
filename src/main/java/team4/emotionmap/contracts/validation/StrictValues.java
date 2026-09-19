package team4.emotionmap.contracts.validation;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.error.FieldError;
import team4.emotionmap.contracts.geo.GeoPoint;

/**
 * 엄격한 스칼라 검증(C03). {@link UUID#fromString} 은 {@code 1-2-3-4-5} 같은 비정규 표기도 받으므로
 * 여기서는 8-4-4-4-12 16진수 표기만 허용한다. 시각은 오프셋이 포함된 ISO 8601 만 받는다.
 */
public final class StrictValues {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private StrictValues() {
    }

    public static boolean isUuid(String value) {
        return value != null && UUID_PATTERN.matcher(value).matches();
    }

    /** 실패 시 {@code errorCode} 와 {@code field INVALID_VALUE}. 누락(null)은 REQUIRED. */
    public static UUID requireUuid(String value, String field, ErrorCode errorCode) {
        if (value == null) {
            throw ContractError.of(errorCode, FieldError.required(field));
        }
        if (!isUuid(value)) {
            throw ContractError.of(errorCode, FieldError.invalid(field));
        }
        return UUID.fromString(value);
    }

    /** 선택 필드용: null 은 그대로 null, 값이 있으면 엄격 검사. */
    public static UUID optionalUuid(String value, String field, ErrorCode errorCode) {
        return value == null ? null : requireUuid(value, field, errorCode);
    }

    public static OffsetDateTime requireOffsetDateTime(String value, String field, ErrorCode errorCode) {
        if (value == null) {
            throw ContractError.of(errorCode, FieldError.required(field));
        }
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw ContractError.of(errorCode, FieldError.invalid(field));
        }
    }

    /** 좌표 두 값을 한 번에 검사해 범위 밖 필드를 모두 보고한다. */
    public static GeoPoint requireCoordinates(Double lat, Double lng, String latField, String lngField,
                                              ErrorCode errorCode) {
        List<FieldError> errors = new java.util.ArrayList<>(2);
        if (lat == null) {
            errors.add(FieldError.required(latField));
        } else if (!GeoPoint.isValidLat(lat)) {
            errors.add(FieldError.outOfRange(latField));
        }
        if (lng == null) {
            errors.add(FieldError.required(lngField));
        } else if (!GeoPoint.isValidLng(lng)) {
            errors.add(FieldError.outOfRange(lngField));
        }
        if (!errors.isEmpty()) {
            throw ContractError.of(errorCode, errors);
        }
        return new GeoPoint(lat, lng);
    }
}
