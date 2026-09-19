package team4.emotionmap.place;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import team4.emotionmap.place.dto.PlaceResponse;

@RestController
@RequestMapping("/v1/places")
@RequiredArgsConstructor
public class PlaceController {
    private final PlaceService placeService;

    @GetMapping
    public List<PlaceResponse> byBoundingBox(@AuthenticationPrincipal UUID userId,
                                           @RequestParam("bbox") String bbox) {
        String[] parts = bbox.split(",", -1);
        if (parts.length != 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected minLng,minLat,maxLng,maxLat");
        }
        try {
            return placeService.findInBoundingBox(userId,
                    Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()), Double.parseDouble(parts[3].trim()));
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid bounding box", e);
        }
    }
}
