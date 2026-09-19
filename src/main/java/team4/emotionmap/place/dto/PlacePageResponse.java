package team4.emotionmap.place.dto;

import java.util.List;
import team4.emotionmap.contracts.page.PageInfo;

public record PlacePageResponse(List<PlaceResponse> items, PageInfo pageInfo) {
}
