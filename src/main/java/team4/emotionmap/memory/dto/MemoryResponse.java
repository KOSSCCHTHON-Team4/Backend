package team4.emotionmap.memory.dto;

import java.time.OffsetDateTime;
import team4.emotionmap.memory.Emotion;
import team4.emotionmap.memory.Memory;
import team4.emotionmap.memory.MemoryStatus;
import team4.emotionmap.memory.Visibility;

/**
 * 기억 응답 DTO.
 * embedding(float[1024]) 같은 내부 데이터는 응답에 포함하지 않는다.
 * emotionTag 는 Claude 추출 전이면 null 일 수 있다.
 *
 * 이미지: DB 에는 key(imagePath)만 있고, 클라이언트가 바로 쓸 수 있게
 *   imageKey(원본 key)와 imageUrl(바이너리 조회 API 경로)을 함께 내려준다.
 *   이미지가 없으면(선택 항목) 둘 다 null.
 */
public record MemoryResponse(
        Long id,
        Long userId,
        Long placeId,
        String content,
        String imageKey,
        String imageUrl,
        Visibility visibility,
        Emotion emotionTag,
        MemoryStatus status,
        OffsetDateTime createdAt
) {
    public static MemoryResponse from(Memory m) {
        String key = m.getImagePath();
        String url = (key == null || key.isBlank()) ? null : "/api/images/" + key;
        return new MemoryResponse(
                m.getId(),
                m.getUserId(),
                m.getPlaceId(),
                m.getContent(),
                key,
                url,
                m.getVisibility(),
                m.getEmotionTag(),
                m.getStatus(),
                m.getCreatedAt()
        );
    }
}
