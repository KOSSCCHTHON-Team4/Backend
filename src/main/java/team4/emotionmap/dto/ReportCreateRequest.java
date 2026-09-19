package team4.emotionmap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 신고 생성 요청 (POST /reports). reason 은 자유 텍스트.
 */
public record ReportCreateRequest(
        @NotNull Long memoryId,
        @NotBlank String reason
) {
}
