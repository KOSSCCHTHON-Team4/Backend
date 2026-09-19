package team4.emotionmap.contracts.fixtures;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import team4.emotionmap.contracts.ai.AnalysisProvenance;
import team4.emotionmap.contracts.dictionary.AnalyzedAtmospheres;
import team4.emotionmap.contracts.dictionary.Atmospheres;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;
import team4.emotionmap.contracts.geo.GeoPoint;
import team4.emotionmap.contracts.memory.AtmosphereAnalysisStatus;
import team4.emotionmap.contracts.memory.AtmosphereSources;
import team4.emotionmap.contracts.memory.AxisSource;
import team4.emotionmap.contracts.memory.CategoryAnalysisStatus;
import team4.emotionmap.contracts.memory.CategoryAssignment;
import team4.emotionmap.contracts.memory.ContentStatus;
import team4.emotionmap.contracts.memory.DataOrigin;
import team4.emotionmap.contracts.memory.DistributionType;
import team4.emotionmap.contracts.memory.MemorySnapshot;
import team4.emotionmap.contracts.memory.ModerationStatus;
import team4.emotionmap.contracts.memory.OriginKind;

/**
 * BE1·BE2·AI 테스트가 공유하는 사전·값 객체 fixture(A03). 모든 값은 합성 데이터이며 실제 사용자·위치가 아니다.
 * JSON 사전 fixture 는 {@code src/test/resources/fixtures/*.v1.json}(API_SPEC 8.3/8.4 예시와 동일).
 */
public final class DictionaryFixtures {

    private DictionaryFixtures() {
    }

    /** API_SPEC 8.8 예시: 조용·아늑·함께·(미결). */
    public static final String SAMPLE_CONTENT =
            "작은 카페의 조용한 창가에서 친구와 함께 책을 읽었다. 작업용 책상이 있고 오래 머물기 편한 아늑한 공간이었다.";
    public static final Atmospheres QUIET_COZY_TOGETHER_LONG = new Atmospheres(-1, -1, 1, -1);
    public static final AnalyzedAtmospheres PARTIAL_STAY_UNKNOWN = new AnalyzedAtmospheres(-1, -1, 1, null);
    public static final List<PlaceCategoryCode> CAFE_STUDY = List.of(PlaceCategoryCode.CAFE, PlaceCategoryCode.STUDY_WORK);
    public static final GeoPoint DEMO_CENTER = new GeoPoint(37.6109, 126.9977);
    public static final AnalysisProvenance MOCK_PROVENANCE = new AnalysisProvenance("mock-analysis", "mock-v1", 1, 1);

    public static UUID userId(int n) {
        return UUID.fromString(String.format("10000000-0000-4000-8000-%012d", n));
    }

    public static UUID memoryId(int n) {
        return UUID.fromString(String.format("30000000-0000-4000-8000-%012d", n));
    }

    public static UUID placeId(int n) {
        return UUID.fromString(String.format("20000000-0000-4000-8000-%012d", n));
    }

    /** 승인된 직접 LETTER 원문(사진 없음). */
    public static MemorySnapshot approvedLetter(UUID id, UUID ownerId, Instant createdAt) {
        return new MemorySnapshot(id, ownerId, placeId(1), DistributionType.LETTER, OriginKind.DIRECT,
                DataOrigin.SYNTHETIC, ContentStatus.ACTIVE, ModerationStatus.APPROVED, createdAt.plusSeconds(60),
                SAMPLE_CONTENT, null, DEMO_CENTER, QUIET_COZY_TOGETHER_LONG,
                new AtmosphereSources(AxisSource.AI, AxisSource.AI, AxisSource.AI, AxisSource.USER), 1,
                AtmosphereAnalysisStatus.PARTIAL, CategoryAnalysisStatus.SUCCEEDED, "mock-analysis", "mock-v1",
                List.of(new CategoryAssignment(PlaceCategoryCode.CAFE, 1, AxisSource.AI),
                        new CategoryAssignment(PlaceCategoryCode.STUDY_WORK, 2, AxisSource.AI)),
                null, createdAt, null);
    }
}
