package team4.emotionmap.memory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import team4.emotionmap.account.dto.Atmospheres;
import team4.emotionmap.memory.DistributionType;

/** Direct manual creation: the server records USER sources and NOT_RUN analysis. */
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
        String placeLabel
) {
}
