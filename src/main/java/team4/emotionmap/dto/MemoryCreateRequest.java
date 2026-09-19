package team4.emotionmap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import team4.emotionmap.domain.Visibility;

/**
 * 기억 생성 요청 DTO.
 *
 * 중요: 감정 태그(emotion)는 요청에서 받지 않는다.
 *       서버가 content 를 Claude(sonnet-5) 로 분석해 자동으로 채운다.
 *       임베딩도 서버가 임베딩 모델로 생성해 채운다.
 *
 * imagePath 는 이미지 업로드 API 로 먼저 저장한 뒤 그 경로를 담아 보낸다(추후 확정).
 */
public record MemoryCreateRequest(
        @NotNull Long userId,
        Long placeId,
        @NotBlank String content,
        String imagePath,
        @NotNull Visibility visibility
) {
}
