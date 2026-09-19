package team4.emotionmap.catalog.dto;

import java.util.List;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;

/** API_SPEC 8.4 {@code PlaceCategoriesResponse}. definition 은 팀 검토 대상 분류 가이드 초안. */
public record PlaceCategoriesResponse(int version, List<Category> items) {

    public record Category(String code, String label, String definition, int order) {
    }

    public static PlaceCategoriesResponse v1() {
        List<Category> items = PlaceCategoryCode.ordered().stream()
                .map(c -> new Category(c.name(), c.label(), c.definition(), c.order()))
                .toList();
        return new PlaceCategoriesResponse(PlaceCategoryCode.TAXONOMY_VERSION, items);
    }
}
