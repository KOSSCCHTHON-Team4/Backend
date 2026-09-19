package team4.emotionmap.contracts.ai;

import java.util.List;

/**
 * 2차 판정 결과. 상류 실패는 {@code failed=true} 이며 이때 {@code fit} 은 항상 false 다 —
 * 애매 구간 후보는 검증 없이는 알림으로 승격되지 않는다(안전한 기본값 = 탈락).
 */
public record VerifyResult(boolean failed, boolean fit, double confidence, String reason, List<String> evidence) {

    public VerifyResult {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        if (Double.isNaN(confidence) || confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("confidence must be within [0, 1]");
        }
        if (failed && fit) {
            throw new IllegalArgumentException("a failed verification is never a fit");
        }
    }

    public static VerifyResult failed(String reason) {
        return new VerifyResult(true, false, 0.0, reason, List.of());
    }

    public static VerifyResult of(boolean fit, double confidence, String reason, List<String> evidence) {
        return new VerifyResult(false, fit, confidence, reason, evidence);
    }
}
