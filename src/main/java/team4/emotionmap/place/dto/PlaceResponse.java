package team4.emotionmap.place.dto;

import java.util.UUID;
import team4.emotionmap.contracts.dictionary.VibeVector;
import team4.emotionmap.place.PlaceCategorySource;
import team4.emotionmap.place.PlaceRepository;

/** 핀 응답. profile 필드는 요청자에게 보이는 적격 DIRECT 경험, memoryCount는 모든 가시 경험에서 계산한다. */
public record PlaceResponse(UUID id, String label, Double lat, Double lng,
                            String naverTitle, String naverAddress,
                            String categoryCode, PlaceCategorySource categorySource,
                            int reviewCount, VibeVector vibe, long memoryCount) {
    public static PlaceResponse from(PlaceRepository.VisiblePlaceRow row, int smoothing) {
        int reviewCount = Math.toIntExact(row.getReviewCount());
        VibeVector vibe = reviewCount == 0 ? null
                : VibeVector.shrunkMeanFromSums(row.getCrowdSum(), row.getSpatialSum(), row.getCompanySum(),
                row.getStaySum(), reviewCount, smoothing);
        PlaceCategorySource categorySource = row.getCategorySource() == null ? null
                : PlaceCategorySource.valueOf(row.getCategorySource());
        return new PlaceResponse(row.getId(), row.getLabel(), row.getLat(), row.getLng(),
                row.getNaverTitle(), row.getNaverAddress(), row.getCategoryCode(), categorySource,
                reviewCount, vibe, row.getMemoryCount());
    }
}
