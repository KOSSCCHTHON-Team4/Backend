package team4.emotionmap.contracts.account;

import team4.emotionmap.contracts.error.ErrorCode;

/** {@code app_users.access_status} (ERD 4.11). 로그인 성공 ≠ ACTIVE. */
public enum AccountStatus {
    PENDING(ErrorCode.INVITATION_REQUIRED),
    ACTIVE(null),
    SUSPENDED(ErrorCode.ACCOUNT_SUSPENDED),
    CLOSED(ErrorCode.ACCOUNT_CLOSED);

    private final ErrorCode denialCode;

    AccountStatus(ErrorCode denialCode) {
        this.denialCode = denialCode;
    }

    public boolean allowsAccess() {
        return denialCode == null;
    }

    /** 접근 거절 시 응답 코드(403). ACTIVE 면 null. */
    public ErrorCode denialCode() {
        return denialCode;
    }
}
