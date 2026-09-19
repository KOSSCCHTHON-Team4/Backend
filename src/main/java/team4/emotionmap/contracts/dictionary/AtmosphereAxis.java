package team4.emotionmap.contracts.dictionary;

import java.util.List;

/**
 * 확정 분위기 4축 사전(ERD 3.2 / API_SPEC 8.3). 축 값은 유한한 {@code [-1, 1]} binary64 이다.
 * 라벨은 기존 endpoint {@code -1}/{@code +1} anchor 에만 있으며 중간값·0 의 라벨을 만들지 않는다.
 * 축 추가·라벨 변경은 운영 중 불가하며 {@link #DEFINITION_VERSION} 을 올리는 마이그레이션으로만 한다.
 *
 * <p>DB 컬럼명({@link #columnName()})은 다른 담당의 엔티티 작성 시 참조용이다.
 */
public enum AtmosphereAxis {
    CROWD_LEVEL(1, "crowd_level", "조용한", "북적이는"),
    SPATIAL_FEEL(2, "spatial_feel", "아늑한", "탁 트인"),
    COMPANY_FIT(3, "company_fit", "혼자 가기 좋은", "함께 가기 좋은"),
    STAY_STYLE(4, "stay_style", "오래 머물기 좋은", "잠깐 들르기 좋은");

    /** {@code axis_definition_version}. API 응답 {@code version} 과 같다. */
    public static final int DEFINITION_VERSION = 2;
    /** Endpoint label/card/filter 방향 선택자다. 연속 축 값의 타입이 아니다. */
    public static final int NEGATIVE = -1;
    /** Endpoint label/card/filter 방향 선택자다. 연속 축 값의 타입이 아니다. */
    public static final int POSITIVE = 1;
    /** 원문 JSON 숫자 token 의 최대 길이. */
    public static final int MAX_JSON_NUMBER_LENGTH = 1000;

    /*
     * token 길이보다 충분히 큰 sentinel 이다. 지수에 더하는 유효숫자 위치 보정은 [-1000, 1000] 안이므로
     * 포화 뒤에도 범위 판정과 확실한 underflow 판정의 부호는 바뀌지 않는다.
     */
    private static final int EXPONENT_SATURATION = 2000;
    private static final List<AtmosphereAxis> ORDERED = List.of(values());

    private final int order;
    private final String columnName;
    private final String negativeLabel;
    private final String positiveLabel;

    AtmosphereAxis(int order, String columnName, String negativeLabel, String positiveLabel) {
        this.order = order;
        this.columnName = columnName;
        this.negativeLabel = negativeLabel;
        this.positiveLabel = positiveLabel;
    }

    /** 표시 순서(1~4). */
    public int order() {
        return order;
    }

    public String columnName() {
        return columnName;
    }

    /** -1 endpoint 의 라벨. */
    public String negativeLabel() {
        return negativeLabel;
    }

    /** +1 endpoint 의 라벨. */
    public String positiveLabel() {
        return positiveLabel;
    }

    /**
     * 정확한 endpoint 에만 기존 라벨을 반환한다. 연속값·0 은 임의의 중간 라벨로 바꾸지 않는다.
     */
    public String labelOf(double value) {
        if (value == NEGATIVE) {
            return negativeLabel;
        }
        if (value == POSITIVE) {
            return positiveLabel;
        }
        throw new IllegalArgumentException("atmosphere axis label exists only at -1 or 1");
    }

    /** 표시 순서대로 고정된 목록. */
    public static List<AtmosphereAxis> ordered() {
        return ORDERED;
    }

    /** 유한하고 양 끝을 포함하는 연속 축 값인지 검사한다. */
    public static boolean isValidValue(double value) {
        return Double.isFinite(value) && value >= -1.0 && value <= 1.0;
    }

    /**
     * 값을 검증하고 signed zero 를 canonical {@code +0.0} 으로 만든다.
     *
     * @throws IllegalArgumentException finite {@code [-1, 1]} 밖의 값일 때
     */
    public static double requireValidValue(double value) {
        if (!isValidValue(value)) {
            throw new IllegalArgumentException("atmosphere axis value must be finite and within [-1, 1]");
        }
        return value == 0.0 ? 0.0 : value;
    }

    /**
     * JSON number 문법을 한 번만 스캔해 수학적 범위를 binary64 변환보다 먼저 검사한다.
     *
     * <p>유효 숫자의 첫 nonzero 자리 {@code k} 가 0 보다 크면 절대값이 1 보다 크다. {@code k == 0} 은
     * 첫 숫자가 1이고 뒤의 significand 가 모두 0일 때만 endpoint 다. {@code k <= -325} 의 nonzero 는
     * binary64 에서 확실히 underflow 하므로 {@code +0.0} 으로 바로 반환하고, {@code k == -324} 근방은
     * {@link Double#parseDouble(String)} 에 맡겨 subnormal 반올림을 보존한다.
     */
    public static double parseJsonNumber(String token) {
        if (token == null || token.isEmpty() || token.length() > MAX_JSON_NUMBER_LENGTH) {
            throw invalidJsonNumber();
        }

        int length = token.length();
        int index = token.charAt(0) == '-' ? 1 : 0;
        if (index == length) {
            throw invalidJsonNumber();
        }

        int integerDigits = 0;
        int leadingZeroDigits = 0;
        char firstNonzero = 0;
        boolean nonzeroAfterFirst = false;

        char firstIntegerDigit = token.charAt(index);
        if (firstIntegerDigit == '0') {
            integerDigits = 1;
            leadingZeroDigits = 1;
            index++;
            if (index < length && isDigit(token.charAt(index))) {
                throw invalidJsonNumber();
            }
        } else if (isNonzeroDigit(firstIntegerDigit)) {
            while (index < length && isDigit(token.charAt(index))) {
                char digit = token.charAt(index++);
                integerDigits++;
                if (firstNonzero == 0) {
                    firstNonzero = digit;
                } else if (digit != '0') {
                    nonzeroAfterFirst = true;
                }
            }
        } else {
            throw invalidJsonNumber();
        }

        if (index < length && token.charAt(index) == '.') {
            int fractionStart = ++index;
            while (index < length && isDigit(token.charAt(index))) {
                char digit = token.charAt(index++);
                if (firstNonzero == 0) {
                    if (digit == '0') {
                        leadingZeroDigits++;
                    } else {
                        firstNonzero = digit;
                    }
                } else if (digit != '0') {
                    nonzeroAfterFirst = true;
                }
            }
            if (index == fractionStart) {
                throw invalidJsonNumber();
            }
        }

        int exponent = 0;
        if (index < length && (token.charAt(index) == 'e' || token.charAt(index) == 'E')) {
            index++;
            boolean negativeExponent = false;
            if (index < length && (token.charAt(index) == '+' || token.charAt(index) == '-')) {
                negativeExponent = token.charAt(index++) == '-';
            }
            if (index == length || !isDigit(token.charAt(index))) {
                throw invalidJsonNumber();
            }

            int magnitude = 0;
            while (index < length && isDigit(token.charAt(index))) {
                int digit = token.charAt(index++) - '0';
                if (magnitude < EXPONENT_SATURATION) {
                    if (magnitude > (EXPONENT_SATURATION - digit) / 10) {
                        magnitude = EXPONENT_SATURATION;
                    } else {
                        magnitude = magnitude * 10 + digit;
                    }
                }
            }
            exponent = negativeExponent ? -magnitude : magnitude;
        }
        if (index != length) {
            throw invalidJsonNumber();
        }

        if (firstNonzero == 0) {
            return 0.0;
        }

        int firstSignificantDecimalPosition = exponent + integerDigits - leadingZeroDigits - 1;
        if (firstSignificantDecimalPosition > 0
                || (firstSignificantDecimalPosition == 0
                && (firstNonzero != '1' || nonzeroAfterFirst))) {
            throw invalidJsonNumber();
        }
        if (firstSignificantDecimalPosition <= -325) {
            return 0.0;
        }

        try {
            return requireValidValue(Double.parseDouble(token));
        } catch (NumberFormatException e) {
            throw invalidJsonNumber();
        }
    }

    private static boolean isDigit(char value) {
        return value >= '0' && value <= '9';
    }

    private static boolean isNonzeroDigit(char value) {
        return value >= '1' && value <= '9';
    }

    private static IllegalArgumentException invalidJsonNumber() {
        return new IllegalArgumentException("atmosphere axis JSON number must be finite and within [-1, 1]");
    }
}
