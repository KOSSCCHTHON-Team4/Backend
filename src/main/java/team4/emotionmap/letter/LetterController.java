package team4.emotionmap.letter;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.error.FieldError;
import team4.emotionmap.letter.dto.LetterLikeResponse;
import team4.emotionmap.letter.dto.LetterPageResponse;
import team4.emotionmap.letter.dto.LetterReadResponse;
import team4.emotionmap.letter.dto.TodayResponse;

@RestController
@RequestMapping("/v1/letters")
@RequiredArgsConstructor
public class LetterController {

    private static final Set<String> LIST_QUERY_FIELDS = Set.of("atmospheres", "categories", "cursor", "limit");

    private final LetterService letterService;

    @GetMapping
    public LetterPageResponse myLetters(@AuthenticationPrincipal UUID userId,
                                        @RequestParam MultiValueMap<String, String> query) {
        validateListQuery(query);
        return letterService.findForReceiver(userId, query.get("atmospheres"), query.get("categories"),
                query.getFirst("cursor"), query.getFirst("limit"));
    }

    @GetMapping("/today")
    public TodayResponse today(@AuthenticationPrincipal UUID userId,
                               @RequestParam MultiValueMap<String, String> query) {
        validateNoQuery(query);
        return letterService.today(userId);
    }

    @PatchMapping("/{deliveryId}/read")
    public LetterReadResponse markRead(@AuthenticationPrincipal UUID userId, @PathVariable UUID deliveryId) {
        return letterService.markRead(userId, deliveryId);
    }

    @PostMapping("/{deliveryId}/like")
    public LetterLikeResponse like(@AuthenticationPrincipal UUID userId, @PathVariable UUID deliveryId) {
        return letterService.like(userId, deliveryId);
    }

    private static void validateListQuery(MultiValueMap<String, String> query) {
        if (query == null) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST);
        }
        for (String field : query.keySet()) {
            if (!LIST_QUERY_FIELDS.contains(field)) {
                throw ContractError.of(ErrorCode.INVALID_REQUEST,
                        FieldError.unknown(field == null ? "query" : field));
            }
        }
        validateSingle(query, "cursor");
        validateSingle(query, "limit");
        validateRepeatedValues(query, "atmospheres");
        validateRepeatedValues(query, "categories");
    }

    private static void validateNoQuery(MultiValueMap<String, String> query) {
        if (query == null || query.isEmpty()) {
            return;
        }
        String field = query.keySet().iterator().next();
        throw ContractError.of(ErrorCode.INVALID_REQUEST, FieldError.unknown(field == null ? "query" : field));
    }

    private static void validateSingle(MultiValueMap<String, String> query, String field) {
        if (!query.containsKey(field)) {
            return;
        }
        List<String> values = query.get(field);
        if (values == null || values.isEmpty()) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST, FieldError.invalid(field));
        }
        if (values.size() != 1) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST, FieldError.duplicate(field));
        }
    }

    private static void validateRepeatedValues(MultiValueMap<String, String> query, String field) {
        if (!query.containsKey(field)) {
            return;
        }
        List<String> values = query.get(field);
        if (values == null || values.isEmpty() || values.stream().anyMatch(value -> value == null || value.isEmpty())) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST, FieldError.invalid(field));
        }
    }
}
