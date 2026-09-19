package team4.emotionmap.notification.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 취향 알림 설정 생성/수정 요청(기획 §4·§9).
 *
 * @param cards          카드 라벨 2~4장(같은 축 반대 카드 불가). 예: ["조용한","혼자 가기 좋은"]
 * @param preferenceText 한 줄 자연어(선택). 2차 판정에만 쓴다
 * @param categoryFilter 카테고리 코드 목록(선택)
 * @param centerLat      기준 위도(선택, 없으면 우편함 위치)
 * @param centerLng      기준 경도(선택)
 * @param radiusM        반경 m(선택, 없으면 설정 기본값)
 * @param active         활성 여부(선택, 기본 true)
 */
public record PreferenceRequest(
        @NotNull @Size(min = 2, max = 4) List<String> cards,
        String preferenceText,
        List<String> categoryFilter,
        Double centerLat,
        Double centerLng,
        Integer radiusM,
        Boolean active
) {
}
