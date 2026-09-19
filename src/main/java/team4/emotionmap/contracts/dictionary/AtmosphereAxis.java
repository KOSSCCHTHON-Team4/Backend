package team4.emotionmap.contracts.dictionary;

import java.util.List;

/**
 * 확정 분위기 4축 사전(ERD 3.2 / API_SPEC 8.3). 값은 항상 {@code -1} 또는 {@code +1}.
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
    public static final int DEFINITION_VERSION = 1;
    public static final int NEGATIVE = -1;
    public static final int POSITIVE = 1;

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

    /** -1 의 라벨. */
    public String negativeLabel() {
        return negativeLabel;
    }

    /** +1 의 라벨. */
    public String positiveLabel() {
        return positiveLabel;
    }

    public String labelOf(int value) {
        requireValidValue(value);
        return value == NEGATIVE ? negativeLabel : positiveLabel;
    }

    /** 표시 순서대로 고정된 목록. */
    public static List<AtmosphereAxis> ordered() {
        return ORDERED;
    }

    public static boolean isValidValue(int value) {
        return value == NEGATIVE || value == POSITIVE;
    }

    public static int requireValidValue(int value) {
        if (!isValidValue(value)) {
            throw new IllegalArgumentException("atmosphere axis value must be -1 or 1");
        }
        return value;
    }
}
