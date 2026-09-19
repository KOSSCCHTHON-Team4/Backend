package team4.emotionmap.memory.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import team4.emotionmap.contracts.ai.AnalysisResult;
import team4.emotionmap.contracts.dictionary.AnalyzedAtmospheres;
import team4.emotionmap.contracts.dictionary.Atmospheres;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.fixtures.DictionaryFixtures;
import team4.emotionmap.contracts.memory.AtmosphereAnalysisStatus;
import team4.emotionmap.contracts.memory.AxisSource;
import team4.emotionmap.contracts.memory.CategoryAnalysisStatus;
import team4.emotionmap.platform.security.HmacSignedValueCodecTestSupport;

/** A10/A11/A12: 본문 수정 뒤 옛 토큰, 만료, 변조, 출처 AI/USER 판정. */
class AnalysisReceiptCodecTest {

    private final AnalysisReceiptCodec codec = new AnalysisReceiptCodec(HmacSignedValueCodecTestSupport.codec());
    private final UUID user = DictionaryFixtures.userId(1);
    private final Instant now = Instant.parse("2026-09-19T02:00:00Z");
    private final AnalysisResult result = AnalysisResult.of(DictionaryFixtures.PARTIAL_STAY_UNKNOWN,
            DictionaryFixtures.CAFE_STUDY, DictionaryFixtures.MOCK_PROVENANCE);

    @Test
    void roundTripPreservesReceiptWithoutStoringContent() {
        AnalysisReceipt receipt = AnalysisReceipt.from(user, DictionaryFixtures.SAMPLE_CONTENT, result, now.plusSeconds(900));
        String token = codec.encode(receipt);
        assertThat(token).doesNotContain("카페");
        AnalysisReceipt back = codec.verify(token, user, DictionaryFixtures.SAMPLE_CONTENT, now);
        assertThat(back).isEqualTo(receipt);
        assertThat(back.atmosphereStatus()).isEqualTo(AtmosphereAnalysisStatus.PARTIAL);
        assertThat(back.categoryStatus()).isEqualTo(CategoryAnalysisStatus.SUCCEEDED);
    }

    @Test
    void editedContentIsMismatch_A10() {
        String token = codec.encode(AnalysisReceipt.from(user, DictionaryFixtures.SAMPLE_CONTENT, result, now.plusSeconds(900)));
        assertThat(catchThrowableOfType(ContractError.class,
                () -> codec.verify(token, user, DictionaryFixtures.SAMPLE_CONTENT + " ", now)).code())
                .isEqualTo(ErrorCode.ANALYSIS_CONTENT_MISMATCH);
    }

    @Test
    void expiredAndForeignTokens() {
        String token = codec.encode(AnalysisReceipt.from(user, "본문", result, now.plusSeconds(10)));
        assertThat(catchThrowableOfType(ContractError.class,
                () -> codec.verify(token, user, "본문", now.plusSeconds(10))).code())
                .isEqualTo(ErrorCode.ANALYSIS_TOKEN_EXPIRED);
        assertThat(catchThrowableOfType(ContractError.class,
                () -> codec.verify(token, DictionaryFixtures.userId(2), "본문", now)).code())
                .isEqualTo(ErrorCode.ANALYSIS_TOKEN_INVALID);
    }

    @Test
    void failedAnalysisReceiptStillRoundTrips_A12() {
        AnalysisResult failed = AnalysisResult.failed(DictionaryFixtures.MOCK_PROVENANCE, "TIMEOUT");
        String token = codec.encode(AnalysisReceipt.from(user, "본문", failed, now.plusSeconds(900)));
        AnalysisReceipt back = codec.verify(token, user, "본문", now);
        assertThat(back.categoryStatus()).isEqualTo(CategoryAnalysisStatus.FAILED);
        assertThat(back.categories()).isEmpty();
        assertThat(back.atmospheres().isEmpty()).isTrue();
    }

    @Test
    void serverDecidesSourcesByComparison() {
        Atmospheres finalValues = new Atmospheres(-1, 1, 1, -1); // SPATIAL 은 AI(-1)와 다르게, STAY 는 AI null → USER
        var sources = AxisSourceResolver.resolveAxes(finalValues, new AnalyzedAtmospheres(-1, -1, 1, null));
        assertThat(sources.crowdLevel()).isEqualTo(AxisSource.AI);
        assertThat(sources.spatialFeel()).isEqualTo(AxisSource.USER);
        assertThat(sources.companyFit()).isEqualTo(AxisSource.AI);
        assertThat(sources.stayStyle()).isEqualTo(AxisSource.USER);
        assertThat(AxisSourceResolver.resolveAxes(finalValues, null).crowdLevel()).isEqualTo(AxisSource.USER);

        var cats = AxisSourceResolver.resolveCategories(List.of(PlaceCategoryCode.CAFE, PlaceCategoryCode.BAR),
                DictionaryFixtures.CAFE_STUDY);
        assertThat(cats).extracting(c -> c.slotNo()).containsExactly(1, 2);
        assertThat(cats.get(0).source()).isEqualTo(AxisSource.AI);
        assertThat(cats.get(1).source()).isEqualTo(AxisSource.USER);
    }
}
