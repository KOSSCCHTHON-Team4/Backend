package team4.emotionmap.place.dto;

import java.util.UUID;
import team4.emotionmap.contracts.dictionary.VibeVector;
import team4.emotionmap.place.Place;
import team4.emotionmap.place.PlaceCategorySource;

/** 핀 응답. 분위기 벡터·카테고리·리뷰 수는 우리 리뷰 집계값이며 리뷰가 없으면 null/0 이다. */
public record PlaceResponse(UUID id, String label, Double lat, Double lng,
                            String naverTitle, String naverAddress,
                            String categoryCode, PlaceCategorySource categorySource,
                            int reviewCount, VibeVector vibe) {
    public static PlaceResponse from(Place place) {
        return new PlaceResponse(place.getId(), place.getLabel(), place.getLat(), place.getLng(),
                place.getNaverTitle(), place.getNaverAddress(),
                place.getCategoryCode(), place.getCategorySource(),
                place.getReviewCount() == null ? 0 : place.getReviewCount(), place.vibe());
    }
}
