package team4.emotionmap.place;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team4.emotionmap.place.dto.PlaceResponse;

@Service
@RequiredArgsConstructor
public class PlaceService {

    private final PlaceRepository placeRepository;

    /** 지도 영역(bbox = minLng,minLat,maxLng,maxLat) 내 핀 조회. */
    @Transactional(readOnly = true)
    public List<PlaceResponse> findInBoundingBox(double minLng, double minLat,
                                                 double maxLng, double maxLat) {
        return placeRepository.findInBoundingBox(minLng, minLat, maxLng, maxLat).stream()
                .map(PlaceResponse::from)
                .toList();
    }
}
