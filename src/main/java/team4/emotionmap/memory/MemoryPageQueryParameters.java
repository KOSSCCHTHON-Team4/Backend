package team4.emotionmap.memory;

import java.util.List;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.error.FieldError;

/** Strict query-shape handling shared by the three memory cursor endpoints. */
final class MemoryPageQueryParameters {

    private static final Set<String> PAGE_FIELDS = Set.of("cursor", "limit");

    private MemoryPageQueryParameters() {
    }

    static PageRequest page(MultiValueMap<String, String> query) {
        validateFields(query, PAGE_FIELDS);
        return new PageRequest(single(query, "cursor", false), single(query, "limit", false));
    }

    static OwnLettersRequest ownLetters(MultiValueMap<String, String> query) {
        validateFields(query, Set.of("type", "cursor", "limit"));
        String type = single(query, "type", true);
        return new OwnLettersRequest(type, single(query, "cursor", false), single(query, "limit", false));
    }

    private static void validateFields(MultiValueMap<String, String> query, Set<String> allowed) {
        if (query == null) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST);
        }
        for (String field : query.keySet()) {
            if (!allowed.contains(field)) {
                throw ContractError.of(ErrorCode.INVALID_REQUEST,
                        FieldError.unknown(field == null ? "query" : field));
            }
        }
    }

    private static String single(MultiValueMap<String, String> query, String field, boolean required) {
        if (!query.containsKey(field)) {
            if (required) {
                throw ContractError.of(ErrorCode.INVALID_REQUEST, FieldError.required(field));
            }
            return null;
        }
        List<String> values = query.get(field);
        if (values == null || values.isEmpty() || values.size() != 1) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST,
                    values != null && values.size() > 1 ? FieldError.duplicate(field) : FieldError.invalid(field));
        }
        return values.getFirst();
    }

    record PageRequest(String cursor, String limit) {
    }

    record OwnLettersRequest(String type, String cursor, String limit) {
    }
}
