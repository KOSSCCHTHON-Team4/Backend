package team4.emotionmap.memory.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import team4.emotionmap.contracts.ai.AnalysisEnrichment;
import team4.emotionmap.contracts.ai.AnalysisPort;
import team4.emotionmap.contracts.ai.AnalysisProvenance;
import team4.emotionmap.contracts.ai.AnalysisRequest;
import team4.emotionmap.contracts.ai.AnalysisResult;
import team4.emotionmap.contracts.dictionary.AnalyzedAtmospheres;
import team4.emotionmap.contracts.dictionary.AtmosphereAxis;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;

/**
 * {@link AnalysisPort} 의 <b>가짜 구현</b>(C09). 모델이 아니라 테스트·FE 연동용 고정 규칙이다.
 *
 * <p>시나리오 마커(본문에 포함하면 동작):
 * <ul>
 *   <li>{@code [[AI_FAIL]]} → 상류 실패(FAILED/FAILED, 축 전부 null)</li>
 *   <li>{@code [[AI_PARTIAL]]} → STAY_STYLE 만 null (PARTIAL)</li>
 *   <li>{@code [[AI_UNCLASSIFIED]]} → 카테고리 근거 부족(INSUFFICIENT, 0개)</li>
 * </ul>
 * 그 외에는 라벨 키워드가 있는 카테고리를 최대 3개까지 고르고(없으면 INSUFFICIENT — OTHER 로 채우지 않는다),
 * 4축은 라벨 어간(조용/북적, 아늑/탁 트인, 혼자/함께, 오래 머물/잠깐)이 본문에 있으면 그 값, <b>없으면 null(근거 없음)</b>
 * 을 준다 — 계약대로 빈 축을 추측하지 않는다. 실제 어댑터로 교체될 때 이 클래스는 삭제된다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = AiProperties.MOCK, matchIfMissing = true)
public class MockAnalysisAdapter implements AnalysisPort {

    static final String FAIL_MARKER = "[[AI_FAIL]]";
    static final String PARTIAL_MARKER = "[[AI_PARTIAL]]";
    static final String UNCLASSIFIED_MARKER = "[[AI_UNCLASSIFIED]]";

    private final AnalysisProvenance provenance;

    public MockAnalysisAdapter(AiProperties properties) {
        this.provenance = new AnalysisProvenance(
                properties.analysisModel() == null ? "mock-analysis" : properties.analysisModel(),
                properties.analysisPromptVersion() == null ? "mock-v1" : properties.analysisPromptVersion(),
                AtmosphereAxis.DEFINITION_VERSION, PlaceCategoryCode.TAXONOMY_VERSION);
    }

    @Override
    public AnalysisResult analyze(AnalysisRequest request) {
        String content = request.content();
        log.debug("mock analysis: contentLength={} timeout={}", content.length(), request.timeout());
        if (content.contains(FAIL_MARKER)) {
            return AnalysisResult.failed(provenance, "MOCK_UPSTREAM_FAILURE");
        }
        AnalyzedAtmospheres atmospheres = new AnalyzedAtmospheres(
                axisValue(content, AtmosphereAxis.CROWD_LEVEL),
                axisValue(content, AtmosphereAxis.SPATIAL_FEEL),
                axisValue(content, AtmosphereAxis.COMPANY_FIT),
                content.contains(PARTIAL_MARKER) ? null : axisValue(content, AtmosphereAxis.STAY_STYLE));
        List<PlaceCategoryCode> categories = content.contains(UNCLASSIFIED_MARKER) ? List.of() : categoriesOf(content);
        return AnalysisResult.of(atmospheres, categories, provenance, enrichmentOf(content, atmospheres, categories));
    }

    /** 라벨 어간 사전. 양쪽 다 있으면 null(근거 상충), 하나만 있으면 그 값, 없으면 null. */
    private static final java.util.Map<AtmosphereAxis, String[]> NEGATIVE_STEMS = java.util.Map.of(
            AtmosphereAxis.CROWD_LEVEL, new String[]{"조용"},
            AtmosphereAxis.SPATIAL_FEEL, new String[]{"아늑"},
            AtmosphereAxis.COMPANY_FIT, new String[]{"혼자"},
            AtmosphereAxis.STAY_STYLE, new String[]{"오래 머물"});
    private static final java.util.Map<AtmosphereAxis, String[]> POSITIVE_STEMS = java.util.Map.of(
            AtmosphereAxis.CROWD_LEVEL, new String[]{"북적"},
            AtmosphereAxis.SPATIAL_FEEL, new String[]{"탁 트인"},
            AtmosphereAxis.COMPANY_FIT, new String[]{"함께"},
            AtmosphereAxis.STAY_STYLE, new String[]{"잠깐"});

    /**
     * 기획 §8 부가 출력의 가짜 값: 근거 = 어간이 들어간 문장 조각, 태그 = 카테고리 라벨, 마스킹 = 전화번호·이메일 패턴,
     * 안전 = {@code [[UNSAFE]]} 마커가 없을 때 true. 실제 모델 품질과 무관한 fixture 다.
     */
    static AnalysisEnrichment enrichmentOf(String content, AnalyzedAtmospheres atmospheres,
                                           List<PlaceCategoryCode> categories) {
        java.util.Map<String, String> evidence = new java.util.LinkedHashMap<>();
        for (AtmosphereAxis axis : AtmosphereAxis.ordered()) {
            Integer v = atmospheres.get(axis);
            if (v == null) {
                continue;
            }
            String[] stems = v == AtmosphereAxis.POSITIVE ? POSITIVE_STEMS.get(axis) : NEGATIVE_STEMS.get(axis);
            for (String stem : stems) {
                int at = content.indexOf(stem);
                if (at >= 0) {
                    int from = Math.max(0, at - 6);
                    int to = Math.min(content.length(), at + stem.length() + 6);
                    evidence.put(axis.labelOf(v), content.substring(from, to).strip());
                    break;
                }
            }
        }
        List<String> tags = categories.stream().map(PlaceCategoryCode::label).toList();
        String masked = content
                .replaceAll("01[016789]-?\\d{3,4}-?\\d{4}", "[전화번호]")
                .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "[이메일]");
        boolean pii = !masked.equals(content);
        boolean unsafe = content.contains("[[UNSAFE]]");
        return new AnalysisEnrichment(evidence, tags, categories.isEmpty() ? null : 0.9, "ai",
                masked, pii, !unsafe, unsafe ? "MOCK_UNSAFE" : null);
    }

    private static Integer axisValue(String content, AtmosphereAxis axis) {
        boolean negative = containsAny(content, NEGATIVE_STEMS.get(axis));
        boolean positive = containsAny(content, POSITIVE_STEMS.get(axis));
        if (negative == positive) {
            return null;
        }
        return positive ? AtmosphereAxis.POSITIVE : AtmosphereAxis.NEGATIVE;
    }

    private static boolean containsAny(String content, String[] stems) {
        for (String stem : stems) {
            if (content.contains(stem)) {
                return true;
            }
        }
        return false;
    }

    /** 카테고리 키워드 어간(mock 전용). OTHER 는 의도적으로 없다 — 실패·미분류를 OTHER 로 채우지 않는다. */
    private static final java.util.Map<PlaceCategoryCode, String[]> CATEGORY_STEMS = java.util.Map.of(
            PlaceCategoryCode.CAFE, new String[]{"카페"},
            PlaceCategoryCode.RESTAURANT, new String[]{"음식점", "식당", "식사"},
            PlaceCategoryCode.BAR, new String[]{"술집", "맥주", "와인"},
            PlaceCategoryCode.PARK_WALK, new String[]{"공원", "산책"},
            PlaceCategoryCode.CULTURE, new String[]{"전시", "공연", "박물관", "미술관"},
            PlaceCategoryCode.STUDY_WORK, new String[]{"공부", "작업"},
            PlaceCategoryCode.SHOPPING, new String[]{"쇼핑", "매장", "시장"});

    private static List<PlaceCategoryCode> categoriesOf(String content) {
        String lower = content.toLowerCase(Locale.ROOT);
        List<PlaceCategoryCode> found = new ArrayList<>();
        for (PlaceCategoryCode code : PlaceCategoryCode.ordered()) {
            String[] stems = CATEGORY_STEMS.get(code);
            if (stems != null && containsAny(lower, stems)) {
                found.add(code);
            }
            if (found.size() == PlaceCategoryCode.MAX_PER_MEMORY) {
                break;
            }
        }
        return found;
    }
}
