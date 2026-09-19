package team4.emotionmap.place;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.error.FieldError;
import team4.emotionmap.place.dto.PlacePageResponse;

@RestController
@RequestMapping("/v1/places")
@RequiredArgsConstructor
public class PlaceController {
    private static final Set<String> QUERY_FIELDS = Set.of("bbox", "cursor", "limit");

    private final PlaceService placeService;

    @Parameters({
            @Parameter(name = "bbox", in = ParameterIn.QUERY, required = true,
                    schema = @Schema(type = "string")),
            @Parameter(name = "cursor", in = ParameterIn.QUERY,
                    schema = @Schema(type = "string")),
            @Parameter(name = "limit", in = ParameterIn.QUERY,
                    schema = @Schema(type = "integer", minimum = "1"))
    })
    @GetMapping
    public PlacePageResponse byBoundingBox(@AuthenticationPrincipal UUID userId,
                                           @Parameter(hidden = true)
                                           @RequestParam MultiValueMap<String, String> query) {
        validateQuery(query);
        return placeService.findInBoundingBox(userId, query.getFirst("bbox"),
                query.getFirst("cursor"), query.getFirst("limit"));
    }

    private static void validateQuery(MultiValueMap<String, String> query) {
        if (query == null) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST, FieldError.required("bbox"));
        }
        for (String field : query.keySet()) {
            if (!QUERY_FIELDS.contains(field)) {
                throw ContractError.of(ErrorCode.INVALID_REQUEST,
                        FieldError.unknown(field == null ? "query" : field));
            }
        }
        if (!query.containsKey("bbox")) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST, FieldError.required("bbox"));
        }
        for (String field : QUERY_FIELDS) {
            if (!query.containsKey(field)) {
                continue;
            }
            List<String> values = query.get(field);
            if (values == null || values.isEmpty()) {
                throw ContractError.of(ErrorCode.INVALID_REQUEST, FieldError.invalid(field));
            }
            if (values.size() != 1) {
                throw ContractError.of(ErrorCode.INVALID_REQUEST, FieldError.duplicate(field));
            }
        }
    }
}
