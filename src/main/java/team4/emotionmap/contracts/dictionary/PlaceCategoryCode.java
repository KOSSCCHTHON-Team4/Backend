package team4.emotionmap.contracts.dictionary;

import java.util.List;
import java.util.Optional;

/**
 * 확정 자체 장소 카테고리 8종(ERD 3.3 / API_SPEC 8.4). 네이버 업종 코드가 아니다.
 * {@link #id()} 는 {@code place_categories.id} seed 값 제안이며, DB seed 마이그레이션은 이 enum 과 1:1 이어야 한다.
 *
 * <p>{@code OTHER} 는 "장소 유형은 알 수 있으나 다른 일곱 유형에 해당하지 않음"이라는 <b>분류값</b>이다.
 * 미분류(근거 부족·AI 실패)는 카테고리 0개이며, 절대 OTHER 로 자동 매핑하지 않는다(불변 규칙 2).
 */
public enum PlaceCategoryCode {
    CAFE(1, "카페", "음료·카페 이용이 중심인 공간"),
    RESTAURANT(2, "음식점", "식사 제공·식사 이용이 중심인 공간"),
    BAR(3, "술집", "술을 마시는 이용이 중심인 공간"),
    PARK_WALK(4, "공원·산책", "공원·산책로 등 걷거나 쉬는 야외 공간"),
    CULTURE(5, "문화", "전시·공연·박물관 등 문화 경험을 위한 공간"),
    STUDY_WORK(6, "공부·작업 공간", "공부·작업 용도가 본문에서 드러나는 공간"),
    SHOPPING(7, "쇼핑", "상품을 둘러보거나 구매하는 매장·시장 등"),
    OTHER(8, "기타", "장소 유형은 알 수 있으나 다른 일곱 유형에 해당하지 않는 경우");

    /** {@code taxonomy_version}. API 응답 {@code version} 과 같다. */
    public static final int TAXONOMY_VERSION = 1;
    /** 경험 1건당 최대 카테고리 수 = memory_categories slot_no 1~3. */
    public static final int MAX_PER_MEMORY = 3;

    private static final List<PlaceCategoryCode> ORDERED = List.of(values());

    private final int id;
    private final String label;
    private final String definition;

    PlaceCategoryCode(int id, String label, String definition) {
        this.id = id;
        this.label = label;
        this.definition = definition;
    }

    /** seed PK(smallint) 제안값. 표시 순서와 같다. */
    public int id() {
        return id;
    }

    /** 표시 순서(1~8). */
    public int order() {
        return id;
    }

    public String label() {
        return label;
    }

    public String definition() {
        return definition;
    }

    public static List<PlaceCategoryCode> ordered() {
        return ORDERED;
    }

    /** 대소문자·공백을 관대하게 받지 않는다. 정확히 일치하는 코드만 인식한다. */
    public static Optional<PlaceCategoryCode> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        for (PlaceCategoryCode c : ORDERED) {
            if (c.name().equals(code)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }
}
