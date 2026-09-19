package team4.emotionmap.dto;

import team4.emotionmap.domain.Place;

/**
 * 장소 응답 DTO (지도 핀).
 */
public record PlaceResponse(
        Long id,
        String name,
        Double lat,
        Double lng
) {
    public static PlaceResponse from(Place p) {
        return new PlaceResponse(p.getId(), p.getName(), p.getLat(), p.getLng());
    }
}
