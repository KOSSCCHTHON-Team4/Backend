package team4.emotionmap.contracts.dictionary;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.error.FieldError;

/**
 * 카테고리 선택 검증기(불변 규칙 2). 0~3개, 사전에 있는 코드만, 중복 없음.
 * 실패는 {@link ErrorCode#INVALID_CATEGORIES}(422) 와 필드 오류로 보고한다.
 * AI 어댑터·BE2 필터·A05 생성이 모두 이 한 곳을 사용한다.
 */
public final class CategorySelection {

    private CategorySelection() {
    }

    /**
     * @param field     오류 보고용 필드 이름(예: {@code categoryCodes})
     * @param rawCodes  요청의 문자열 코드 목록(null 은 빈 목록으로 본다)
     * @return 입력 순서를 유지한 검증된 코드 목록
     */
    public static List<PlaceCategoryCode> validate(String field, List<String> rawCodes) {
        List<String> codes = rawCodes == null ? List.of() : rawCodes;
        List<FieldError> errors = new ArrayList<>();
        if (codes.size() > PlaceCategoryCode.MAX_PER_MEMORY) {
            errors.add(FieldError.outOfRange(field));
        }
        List<PlaceCategoryCode> result = new ArrayList<>(codes.size());
        EnumSet<PlaceCategoryCode> seen = EnumSet.noneOf(PlaceCategoryCode.class);
        for (int i = 0; i < codes.size(); i++) {
            String indexed = field + "[" + i + "]";
            PlaceCategoryCode parsed = PlaceCategoryCode.fromCode(codes.get(i)).orElse(null);
            if (parsed == null) {
                errors.add(FieldError.invalid(indexed));
                continue;
            }
            if (!seen.add(parsed)) {
                errors.add(FieldError.duplicate(indexed));
                continue;
            }
            result.add(parsed);
        }
        if (!errors.isEmpty()) {
            throw ContractError.of(ErrorCode.INVALID_CATEGORIES, errors);
        }
        return List.copyOf(result);
    }

    /** 이미 enum 인 목록의 개수·중복 불변 조건 확인(포트 값 객체 생성 시 사용). */
    public static List<PlaceCategoryCode> requireValid(List<PlaceCategoryCode> codes) {
        List<PlaceCategoryCode> list = codes == null ? List.of() : codes;
        if (list.size() > PlaceCategoryCode.MAX_PER_MEMORY) {
            throw new IllegalArgumentException("at most " + PlaceCategoryCode.MAX_PER_MEMORY + " categories");
        }
        EnumSet<PlaceCategoryCode> seen = EnumSet.noneOf(PlaceCategoryCode.class);
        for (PlaceCategoryCode code : list) {
            if (code == null || !seen.add(code)) {
                throw new IllegalArgumentException("categories must be non-null and distinct");
            }
        }
        return List.copyOf(list);
    }
}
