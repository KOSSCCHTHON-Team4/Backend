package team4.emotionmap.contracts.ai;

import java.util.List;
import java.util.Map;

/**
 * 기획 §8 {@code /ai/analyze} 의 부가 출력. 실제 AI 서버가 아직 주지 않는 필드는 null 로 남는다(추측 금지).
 *
 * @param evidence           축 라벨 → 근거 문구 (예: "조용한" → "사람이 거의 없어서")
 * @param tags               자유 태그 (예: 창가, 독서)
 * @param categoryConfidence 카테고리 추론 신뢰도 0~1
 * @param categorySource     "naver" | "ai"
 * @param maskedContent      개인정보 마스킹된 본문. 마스킹이 없으면 원문과 같거나 null
 * @param piiFound           개인정보 발견 여부
 * @param safe               안전 판정(분석 단계). 최종 배달 승인은 ModerationPort 가 별도로 정한다
 * @param unsafeReason       safe=false 일 때 짧은 사유 코드/문구
 */
public record AnalysisEnrichment(
        Map<String, String> evidence,
        List<String> tags,
        Double categoryConfidence,
        String categorySource,
        String maskedContent,
        Boolean piiFound,
        Boolean safe,
        String unsafeReason
) {
    public static final AnalysisEnrichment NONE = new AnalysisEnrichment(null, null, null, null, null, null, null, null);

    public AnalysisEnrichment {
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
        tags = tags == null ? List.of() : List.copyOf(tags);
        if (categoryConfidence != null && (categoryConfidence.isNaN() || categoryConfidence < 0 || categoryConfidence > 1)) {
            throw new IllegalArgumentException("categoryConfidence must be within [0, 1]");
        }
    }

    public boolean isUnsafe() {
        return Boolean.FALSE.equals(safe);
    }

    public boolean hasMaskedContent(String original) {
        return maskedContent != null && !maskedContent.isBlank() && !maskedContent.equals(original);
    }
}
