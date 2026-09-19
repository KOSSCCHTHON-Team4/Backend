package team4.emotionmap.place;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import team4.emotionmap.place.dto.PlaceResponse;

/**
 * 장소 API.
 *   GET /places?bbox=minLng,minLat,maxLng,maxLat  -> 영역 내 핀
 */
@RestController
@RequestMapping("/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceService placeService;

    @GetMapping
    public List<PlaceResponse> byBoundingBox(@RequestParam("bbox") String bbox) {
        // bbox = "minLng,minLat,maxLng,maxLat"
        String[] p = bbox.split(",");
        if (p.length != 4) {
            throw new IllegalArgumentException("bbox 형식은 minLng,minLat,maxLng,maxLat 이어야 합니다.");
        }
        double minLng = Double.parseDouble(p[0].trim());
        double minLat = Double.parseDouble(p[1].trim());
        double maxLng = Double.parseDouble(p[2].trim());
        double maxLat = Double.parseDouble(p[3].trim());
        return placeService.findInBoundingBox(minLng, minLat, maxLng, maxLat);
    }
}
