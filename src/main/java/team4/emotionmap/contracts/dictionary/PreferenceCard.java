package team4.emotionmap.contracts.dictionary;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.error.FieldError;

/**
 * 취향 키워드 카드 8장(기획 §4). 각 카드는 한 축의 한쪽 값이다: 왼쪽 -1, 오른쪽 +1.
 * 카드 식별자는 축 사전({@link AtmosphereAxis})의 라벨과 정확히 같다 — 프롬프트·카드 UI·DB 가 같은 문자열을 쓴다.
 *
 * <p>선택 규칙: 2~4장, 같은 축의 반대 카드 동시 선택 불가. 고르지 않은 축은 0.
 */
public enum PreferenceCard {
    QUIET(AtmosphereAxis.CROWD_LEVEL, AtmosphereAxis.NEGATIVE),
    BUSTLING(AtmosphereAxis.CROWD_LEVEL, AtmosphereAxis.POSITIVE),
    COZY(AtmosphereAxis.SPATIAL_FEEL, AtmosphereAxis.NEGATIVE),
    OPEN(AtmosphereAxis.SPATIAL_FEEL, AtmosphereAxis.POSITIVE),
    SOLO(AtmosphereAxis.COMPANY_FIT, AtmosphereAxis.NEGATIVE),
    TOGETHER(AtmosphereAxis.COMPANY_FIT, AtmosphereAxis.POSITIVE),
    LINGER(AtmosphereAxis.STAY_STYLE, AtmosphereAxis.NEGATIVE),
    QUICK_STOP(AtmosphereAxis.STAY_STYLE, AtmosphereAxis.POSITIVE);

    public static final int MIN_SELECTION = 2;
    public static final int MAX_SELECTION = 4;

    private final AtmosphereAxis axis;
    private final int value;

    PreferenceCard(AtmosphereAxis axis, int value) {
        this.axis = axis;
        this.value = value;
    }

    public AtmosphereAxis axis() {
        return axis;
    }

    public int value() {
        return value;
    }

    /** 카드 표시 문자열 = 축 라벨(예: "조용한", "함께 가기 좋은"). */
    public String label() {
        return axis.labelOf(value);
    }

    public static Optional<PreferenceCard> fromLabel(String label) {
        if (label == null) {
            return Optional.empty();
        }
        for (PreferenceCard card : values()) {
            if (card.label().equals(label)) {
                return Optional.of(card);
            }
        }
        return Optional.empty();
    }

    /**
     * 라벨 목록을 검증해 카드로 바꾼다. 실패는 422 VALIDATION_ERROR 와 {@code cards[i]} 필드 오류.
     * 개수 위반은 {@code cards} OUT_OF_RANGE, 미지 라벨 INVALID_VALUE, 같은 카드 DUPLICATE, 같은 축 반대 카드 INVALID_VALUE.
     */
    public static List<PreferenceCard> parse(String field, List<String> labels) {
        List<String> input = labels == null ? List.of() : labels;
        List<FieldError> errors = new ArrayList<>();
        if (input.size() < MIN_SELECTION || input.size() > MAX_SELECTION) {
            errors.add(FieldError.outOfRange(field));
        }
        List<PreferenceCard> cards = new ArrayList<>();
        Map<AtmosphereAxis, PreferenceCard> byAxis = new EnumMap<>(AtmosphereAxis.class);
        for (int i = 0; i < input.size(); i++) {
            String path = field + "[" + i + "]";
            PreferenceCard card = fromLabel(input.get(i)).orElse(null);
            if (card == null) {
                errors.add(FieldError.invalid(path));
                continue;
            }
            if (cards.contains(card)) {
                errors.add(FieldError.duplicate(path));
                continue;
            }
            PreferenceCard other = byAxis.get(card.axis);
            if (other != null) {
                errors.add(FieldError.invalid(path)); // 같은 축의 반대 카드
                continue;
            }
            byAxis.put(card.axis, card);
            cards.add(card);
        }
        if (!errors.isEmpty()) {
            throw ContractError.of(ErrorCode.VALIDATION_ERROR, errors);
        }
        return List.copyOf(cards);
    }

    /** 선택 카드 → 취향 벡터 U (안 고른 축 0). */
    public static VibeVector toVector(List<PreferenceCard> cards) {
        double[] v = new double[4];
        for (PreferenceCard card : cards) {
            v[card.axis.order() - 1] = card.value;
        }
        return new VibeVector(v[0], v[1], v[2], v[3]);
    }
}
