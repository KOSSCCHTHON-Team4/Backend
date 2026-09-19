package team4.emotionmap.contracts.ai;

/** 알림 문구. 상류 실패면 {@code failed=true} 이고 호출자가 고정 템플릿 문구로 대체한다. */
public record MatchReasonResult(boolean failed, String reason) {

    public static MatchReasonResult unavailable() {
        return new MatchReasonResult(true, null);
    }

    public static MatchReasonResult of(String reason) {
        if (reason == null || reason.isBlank()) {
            return unavailable();
        }
        return new MatchReasonResult(false, reason.strip());
    }
}
