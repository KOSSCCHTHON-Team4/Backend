package team4.emotionmap.memory.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /v1/memories/analyze} 요청(API_SPEC 8.8 + 기획 §8). 본문만 분석하며 사진·계정 취향은 쓰지 않는다.
 *
 * @param naverCategory 네이버 등록 장소면 그 카테고리 원문(예: "카페,디저트>카페"). 미등록이면 null
 */
public record AnalyzeRequest(@NotBlank String content, String naverCategory) {
}
