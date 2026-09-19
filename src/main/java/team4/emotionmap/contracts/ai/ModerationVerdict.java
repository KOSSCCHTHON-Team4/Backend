package team4.emotionmap.contracts.ai;

import team4.emotionmap.contracts.memory.ModerationStatus;

/** 안전 검사 판정. 분류 실패와 다르다 — REJECTED/ERROR 는 수동 보완으로 배달 승인이 되지 않는다. */
public enum ModerationVerdict {
    APPROVED(ModerationStatus.APPROVED),
    REVIEW_REQUIRED(ModerationStatus.REVIEW_REQUIRED),
    REJECTED(ModerationStatus.REJECTED),
    /** 상류 시간 초과·장애. DB 상태 기반 재시도 대상. */
    ERROR(ModerationStatus.ERROR);

    private final ModerationStatus status;

    ModerationVerdict(ModerationStatus status) {
        this.status = status;
    }

    public ModerationStatus toStatus() {
        return status;
    }
}
