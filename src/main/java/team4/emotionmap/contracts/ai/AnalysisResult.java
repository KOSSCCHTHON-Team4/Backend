package team4.emotionmap.contracts.ai;

import java.util.List;
import java.util.Objects;
import team4.emotionmap.contracts.dictionary.AnalyzedAtmospheres;
import team4.emotionmap.contracts.dictionary.CategorySelection;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;
import team4.emotionmap.contracts.memory.AtmosphereAnalysisStatus;
import team4.emotionmap.contracts.memory.CategoryAnalysisStatus;

/**
 * 분류 결과. 불변 조건(생성 시 검사):
 * <ul>
 *   <li>atmosphereStatus 는 축 개수로 결정: 4개=SUCCEEDED, 1~3개=PARTIAL, 0개=FAILED. NOT_RUN 은 포트 결과로 불가.</li>
 *   <li>categoryStatus SUCCEEDED ⇒ 카테고리 1~3개, INSUFFICIENT/FAILED ⇒ 0개. 실패를 OTHER 로 채우지 않는다.</li>
 *   <li>{@code failureReason} 은 운영 진단용 짧은 코드이며 본문·사용자 정보를 넣지 않는다.</li>
 * </ul>
 * AI 상류 실패는 예외가 아니라 FAILED 결과다. 우리 서버 자체 장애(어댑터 설정 오류 등)만 예외로 올린다.
 */
public record AnalysisResult(
        AnalyzedAtmospheres atmospheres,
        List<PlaceCategoryCode> categories,
        CategoryAnalysisStatus categoryStatus,
        AtmosphereAnalysisStatus atmosphereStatus,
        AnalysisProvenance provenance,
        String failureReason
) {
    public AnalysisResult {
        Objects.requireNonNull(atmospheres, "atmospheres");
        Objects.requireNonNull(categoryStatus, "categoryStatus");
        Objects.requireNonNull(atmosphereStatus, "atmosphereStatus");
        Objects.requireNonNull(provenance, "provenance");
        categories = CategorySelection.requireValid(categories);

        AtmosphereAnalysisStatus expected = statusFor(atmospheres);
        if (atmosphereStatus != expected) {
            throw new IllegalArgumentException("atmosphereStatus must be " + expected + " for " + atmospheres.knownCount() + " known axes");
        }
        switch (categoryStatus) {
            case SUCCEEDED -> {
                if (categories.isEmpty()) {
                    throw new IllegalArgumentException("SUCCEEDED requires 1..3 categories");
                }
            }
            case INSUFFICIENT, FAILED -> {
                if (!categories.isEmpty()) {
                    throw new IllegalArgumentException(categoryStatus + " must not carry categories");
                }
            }
            case NOT_RUN -> throw new IllegalArgumentException("port results are never NOT_RUN");
        }
    }

    public static AtmosphereAnalysisStatus statusFor(AnalyzedAtmospheres atmospheres) {
        int known = atmospheres.knownCount();
        if (known == 4) {
            return AtmosphereAnalysisStatus.SUCCEEDED;
        }
        return known == 0 ? AtmosphereAnalysisStatus.FAILED : AtmosphereAnalysisStatus.PARTIAL;
    }

    /** 상류 실패·시간 초과: 축 전부 null, 카테고리 없음. */
    public static AnalysisResult failed(AnalysisProvenance provenance, String failureReason) {
        return new AnalysisResult(AnalyzedAtmospheres.ALL_UNKNOWN, List.of(),
                CategoryAnalysisStatus.FAILED, AtmosphereAnalysisStatus.FAILED, provenance, failureReason);
    }

    /** 정상 실행 결과. 상태는 값에서 파생한다. 카테고리 0개는 INSUFFICIENT. */
    public static AnalysisResult of(AnalyzedAtmospheres atmospheres, List<PlaceCategoryCode> categories,
                                    AnalysisProvenance provenance) {
        List<PlaceCategoryCode> cats = CategorySelection.requireValid(categories);
        return new AnalysisResult(atmospheres, cats,
                cats.isEmpty() ? CategoryAnalysisStatus.INSUFFICIENT : CategoryAnalysisStatus.SUCCEEDED,
                statusFor(atmospheres), provenance, null);
    }

    public boolean isUpstreamFailure() {
        return atmosphereStatus == AtmosphereAnalysisStatus.FAILED && categoryStatus == CategoryAnalysisStatus.FAILED;
    }
}
