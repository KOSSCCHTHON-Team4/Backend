package team4.emotionmap.memory.analysis;

import java.util.ArrayList;
import java.util.List;
import team4.emotionmap.contracts.dictionary.AnalyzedAtmospheres;
import team4.emotionmap.contracts.dictionary.AtmosphereAxis;
import team4.emotionmap.contracts.dictionary.Atmospheres;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;
import team4.emotionmap.contracts.memory.AtmosphereSources;
import team4.emotionmap.contracts.memory.AxisSource;
import team4.emotionmap.contracts.memory.CategoryAssignment;

/**
 * 최종 저장값 vs AI 제안 비교로 AI/USER 출처를 <b>서버가</b> 판정한다(A04). 클라이언트 플래그는 보지 않는다.
 * 규칙: 축은 정규화된 binary64 bits 가 AI 제안과 같으면 AI, 제안이 null 이었거나 bits 가 다르면 USER.
 * 카테고리는 AI 제안 목록에 있으면 AI. 영수증이 없으면(수동 저장) 전부 USER.
 */
public final class AxisSourceResolver {

    private AxisSourceResolver() {
    }

    public static AtmosphereSources resolveAxes(Atmospheres finalValues, AnalyzedAtmospheres suggested) {
        if (suggested == null) {
            return AtmosphereSources.ALL_USER;
        }
        return new AtmosphereSources(
                sourceOf(finalValues, suggested, AtmosphereAxis.CROWD_LEVEL),
                sourceOf(finalValues, suggested, AtmosphereAxis.SPATIAL_FEEL),
                sourceOf(finalValues, suggested, AtmosphereAxis.COMPANY_FIT),
                sourceOf(finalValues, suggested, AtmosphereAxis.STAY_STYLE));
    }

    private static AxisSource sourceOf(Atmospheres finalValues, AnalyzedAtmospheres suggested, AtmosphereAxis axis) {
        Double suggestedValue = suggested.get(axis);
        return suggestedValue != null
                && Double.doubleToLongBits(suggestedValue) == Double.doubleToLongBits(finalValues.get(axis))
                ? AxisSource.AI : AxisSource.USER;
    }

    /** slot_no 는 입력 순서 1..n 으로 부여한다(관련도 순위가 아니다). */
    public static List<CategoryAssignment> resolveCategories(List<PlaceCategoryCode> finalCodes,
                                                             List<PlaceCategoryCode> suggested) {
        List<PlaceCategoryCode> ai = suggested == null ? List.of() : suggested;
        List<CategoryAssignment> result = new ArrayList<>(finalCodes.size());
        for (int i = 0; i < finalCodes.size(); i++) {
            PlaceCategoryCode code = finalCodes.get(i);
            result.add(new CategoryAssignment(code, i + 1, ai.contains(code) ? AxisSource.AI : AxisSource.USER));
        }
        return List.copyOf(result);
    }
}
