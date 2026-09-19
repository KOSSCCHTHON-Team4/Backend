package team4.emotionmap.memory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import team4.emotionmap.contracts.dictionary.Atmospheres;
import team4.emotionmap.contracts.memory.DistributionType;

/**
 * 경험 생성 요청(API_SPEC 8.9 + 기획 §1·§5). {@code analysisToken} 이 있으면 분석 결과와 비교해 AI/USER 출처를 서버가 정하고,
 * 없으면 USER/NOT_RUN 으로 기록한다. 네이버 장소 정보(상호명·주소·카테고리)는 선택이며 있으면 카테고리 매핑이 우선한다.
 * ownerId/originKind/moderationStatus/dataOrigin 은 받지 않는다(미지 필드는 400).
 */
public record MemoryCreateRequest(
        @NotNull DistributionType type,
        @NotNull Double lat,
        @NotNull Double lng,
        @NotBlank String content,
        UUID imageId,
        @NotNull @Valid Atmospheres atmospheres,
        @NotNull @Size(max = 3) List<@NotBlank String> categoryCodes,
        String analysisToken,
        UUID placeId,
        String placeLabel,
        String naverTitle,
        String naverAddress,
        String naverCategory
) {
    public boolean hasNaverPlace() {
        return naverTitle != null && !naverTitle.isBlank();
    }
}
