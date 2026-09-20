package team4.emotionmap.memory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;
import team4.emotionmap.contracts.page.PageInfo;

/** Cursor page for every HTTP memory collection. */
public record MemoryPageResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<MemoryResponse> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PageInfo pageInfo
) {
    public MemoryPageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        Objects.requireNonNull(pageInfo, "pageInfo");
    }
}
