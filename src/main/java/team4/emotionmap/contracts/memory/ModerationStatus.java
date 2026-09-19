package team4.emotionmap.contracts.memory;

/**
 * {@code memories.moderation_status} (ERD 4.11). LETTER 는 APPROVED 이고 최초 {@code available_at} 이
 * 설정된 뒤에만 일일 후보다. NOT_REQUIRED 는 PRIVATE 전용 운영 표현이며 LETTER 에는 금지.
 * 안전 실패(REJECTED/ERROR)는 분류 실패와 달리 수동 보완으로 배달 승인이 되지 않는다.
 */
public enum ModerationStatus {
    PENDING, APPROVED, REVIEW_REQUIRED, REJECTED, ERROR, NOT_REQUIRED;

    public boolean allowsDelivery() {
        return this == APPROVED;
    }
}
