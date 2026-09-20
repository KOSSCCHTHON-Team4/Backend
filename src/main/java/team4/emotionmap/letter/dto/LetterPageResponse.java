package team4.emotionmap.letter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;
import team4.emotionmap.contracts.page.PageInfo;

/** Cursor page of the receiver's immutable delivery history. */
public record LetterPageResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<LetterResponse> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PageInfo pageInfo
) {
    public LetterPageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        Objects.requireNonNull(pageInfo, "pageInfo");
    }
}
