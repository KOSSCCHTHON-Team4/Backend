package team4.emotionmap.catalog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import team4.emotionmap.catalog.dto.AtmosphereAxesResponse;
import team4.emotionmap.catalog.dto.PlaceCategoriesResponse;
import team4.emotionmap.catalog.dto.ServiceConfigResponse;
import team4.emotionmap.contracts.config.ServiceConfigSource;

/**
 * A03 사전·설정 API. 인증 필요(Bearer), <b>온보딩 전 허용 경로</b>다(API_SPEC 2.1).
 * 계정 상태와 온보딩 전 허용 경로는 인증 필터의 AccountAccessGuard 가 매 요청 확인한다.
 * 사용자가 반경·사전을 바꿀 수 있는 경로는 없다.
 */
@Tag(name = "catalog", description = "서비스 설정·4축·8종 카테고리 사전 (온보딩 전 허용)")
@RestController
@RequiredArgsConstructor
public class CatalogController {

    private static final AtmosphereAxesResponse AXES = AtmosphereAxesResponse.v1();
    private static final PlaceCategoriesResponse CATEGORIES = PlaceCategoriesResponse.v1();

    private final ServiceConfigSource serviceConfig;

    @Operation(operationId = "getConfig", summary = "고정 반경·제한·정기 배달 설정",
            description = "값은 서버 설정에서 오며 누락 시 503 CONFIGURATION_UNAVAILABLE. FE 는 기본 반경을 추정하지 않는다.")
    @GetMapping("/v1/config")
    public ServiceConfigResponse getConfig() {
        return ServiceConfigResponse.from(serviceConfig.current());
    }

    @Operation(operationId = "getAtmosphereAxes", summary = "확정 분위기 4축 사전 (version=1)")
    @GetMapping("/v1/atmosphere-axes")
    public AtmosphereAxesResponse getAtmosphereAxes() {
        return AXES;
    }

    @Operation(operationId = "getPlaceCategories", summary = "자체 장소 카테고리 8종 (version=1)")
    @GetMapping("/v1/place-categories")
    public PlaceCategoriesResponse getPlaceCategories() {
        return CATEGORIES;
    }
}
