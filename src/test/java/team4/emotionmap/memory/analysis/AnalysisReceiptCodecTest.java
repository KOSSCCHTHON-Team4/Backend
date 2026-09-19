package team4.emotionmap.memory.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import team4.emotionmap.contracts.signing.ContentHash;
import team4.emotionmap.contracts.signing.SignedClaims;
import team4.emotionmap.contracts.signing.SignedValueCodec;
import team4.emotionmap.contracts.signing.SignedValuePurpose;
import team4.emotionmap.platform.security.HmacSignedValueCodecTestSupport;

/** A10/A11/A12: 본문 수정 뒤 옛 토큰, 만료, v3 bits, 출처 AI/USER 판정. */
class AnalysisReceiptCodecTest {

    private final SignedValueCodec signer = HmacSignedValueCodecTestSupport.codec();
    private final AnalysisReceiptCodec codec = new AnalysisReceiptCodec(signer);
    private final UUID user = DictionaryFixtures.userId(1);
    private final Instant now = Instant.parse("2026-09-19T02:00:00Z");
    private final AnalysisResult result = AnalysisResult.of(DictionaryFixtures.PARTIAL_STAY_UNKNOWN,
            DictionaryFixtures.CAFE_STUDY, DictionaryFixtures.MOCK_PROVENANCE);

    @Test
    void roundTripPreservesV3CanonicalDoubleBitsWithoutStoringContent() {
        double adjacent = Math.nextUp(0.25);
        AnalysisResult precise = AnalysisResult.of(new AnalyzedAtmospheres(-0.0, adjacent, -Double.MIN_VALUE, null),
                List.of(PlaceCategoryCode.CAFE), DictionaryFixtures.MOCK_PROVENANCE);
        AnalysisReceipt receipt = AnalysisReceipt.from(user, DictionaryFixtures.SAMPLE_CONTENT, precise, now.plusSeconds(900));

        String token = codec.encode(receipt);
        assertThat(token).doesNotContain("카페");
        AnalysisReceipt back = codec.verify(token, user, DictionaryFixtures.SAMPLE_CONTENT, now);

        assertThat(back).isEqualTo(receipt);
        assertThat(Double.doubleToLongBits(back.atmospheres().crowdLevel())).isEqualTo(0L);
        assertThat(Double.doubleToLongBits(back.atmospheres().spatialFeel()))
                .isEqualTo(Double.doubleToLongBits(adjacent));
        assertThat(Double.doubleToLongBits(back.atmospheres().companyFit()))
                .isEqualTo(Double.doubleToLongBits(-Double.MIN_VALUE));
        assertThat(back.atmospheres().stayStyle()).isNull();
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
    void signedLegacyOrNoncanonicalPayloadIsInvalid() {
        String legacy = signer.sign(new SignedClaims(SignedValuePurpose.ANALYSIS_TOKEN, user,
                AnalysisReceiptCodec.PAYLOAD_VERSION - 1, now.plusSeconds(900), Map.of()));
        assertInvalid(legacy);

        assertInvalid(signV3("axes-v2:8000000000000000,3fd0000000000000,?,?", "2"));
        assertInvalid(signV3("axes-v2:0000000000000000,3fd0000000000000,?,?", "1"));
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
    void serverDecidesSourcesByCanonicalBitsIncludingZero() {
        Atmospheres finalValues = new Atmospheres(0.0, 0.25, -Double.MIN_VALUE, -1.0);
        var sources = AxisSourceResolver.resolveAxes(finalValues,
                new AnalyzedAtmospheres(-0.0, Math.nextUp(0.25), -Double.MIN_VALUE, null));
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

    private String signV3(String axes, String axisDefinitionVersion) {
        return signer.sign(new SignedClaims(SignedValuePurpose.ANALYSIS_TOKEN, user,
                AnalysisReceiptCodec.PAYLOAD_VERSION, now.plusSeconds(900), Map.of(
                "h", ContentHash.sha256Hex("본문"),
                "a", axes,
                "c", "CAFE",
                "as", "PARTIAL",
                "cs", "SUCCEEDED",
                "m", "mock-analysis",
                "pv", "mock-v2",
                "ad", axisDefinitionVersion,
                "tx", "1"
        )));
    }

    private void assertInvalid(String token) {
        assertThat(catchThrowableOfType(ContractError.class, () -> codec.verify(token, user, "본문", now)).code())
                .isEqualTo(ErrorCode.ANALYSIS_TOKEN_INVALID);
    }
}
