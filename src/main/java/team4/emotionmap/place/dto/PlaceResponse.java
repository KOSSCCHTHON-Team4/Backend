package team4.emotionmap.place.dto;

import java.util.UUID;
import team4.emotionmap.place.Place;

public record PlaceResponse(UUID id, String label, Double lat, Double lng) {
    public static PlaceResponse from(Place place) {
        return new PlaceResponse(place.getId(), place.getLabel(), place.getLat(), place.getLng());
    }
}
