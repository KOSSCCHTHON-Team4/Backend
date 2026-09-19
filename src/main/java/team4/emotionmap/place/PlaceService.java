package team4.emotionmap.place;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import team4.emotionmap.place.dto.PlaceResponse;

@Service
@RequiredArgsConstructor
public class PlaceService {

    private final PlaceRepository placeRepository;

    /** 지도 영역(bbox = minLng,minLat,maxLng,maxLat) 내 핀 조회. */
    @Transactional(readOnly = true)
    public List<PlaceResponse> findInBoundingBox(UUID userId, double minLng, double minLat,
                                                 double maxLng, double maxLat) {
        if (!Double.isFinite(minLng) || !Double.isFinite(maxLng)
                || !Double.isFinite(minLat) || !Double.isFinite(maxLat)
                || minLng < -180 || maxLng > 180 || minLat < -90 || maxLat > 90
                || minLng > maxLng || minLat > maxLat) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid bounding box");
        }
        return placeRepository.findInBoundingBox(userId, minLng, minLat, maxLng, maxLat).stream()
                .map(PlaceResponse::from)
                .toList();
    }
}
