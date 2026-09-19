package team4.emotionmap.contracts.ai;

import java.time.Duration;
import java.util.Objects;
import team4.emotionmap.contracts.dictionary.AtmosphereAxis;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;

/**
 * 분류 입력. <b>본문</b>과 선택적 네이버 카테고리 문자열만 보낸다 — 계정 취향·사진·주변 업체 정보로 빈 축을 추측하지 않는다(A04).
 * 사전 버전을 함께 보내 AI 가 같은 코드 체계를 쓰게 한다.
 *
 * @param naverCategory 네이버 등록 장소의 카테고리 원문(예: "카페,디저트>카페"). 미등록 장소면 null
 */
public record AnalysisRequest(String content, String naverCategory, int axisDefinitionVersion, int taxonomyVersion,
                              Duration timeout) {

    public AnalysisRequest {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(timeout, "timeout");
        if (content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (naverCategory != null && naverCategory.isBlank()) {
            naverCategory = null;
        }
    }

    public static AnalysisRequest of(String content, Duration timeout) {
        return of(content, null, timeout);
    }

    public static AnalysisRequest of(String content, String naverCategory, Duration timeout) {
        return new AnalysisRequest(content, naverCategory, AtmosphereAxis.DEFINITION_VERSION,
                PlaceCategoryCode.TAXONOMY_VERSION, timeout);
    }
}
