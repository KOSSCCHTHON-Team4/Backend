package team4.emotionmap.place.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import team4.emotionmap.contracts.dictionary.VibeVector;
import team4.emotionmap.platform.web.json.StrictJson;
import tools.jackson.databind.json.JsonMapper;

/**
 * GET /v1/places 응답의 vibe 는 장소 프로필 실수 벡터다(경험 저장용 ±1 Atmospheres 와 다른 타입).
 * 앱과 같은 규칙의 JsonMapper(StrictJson)로 -1..1 소수 전 구간이 정밀도 손실 없이 직렬화되는지 확인한다.
 */
class PlaceResponseTest {

    private final JsonMapper mapper = StrictJson.mapper();

    private static final double[] SWEEP = {-1.0, -0.87654321, -0.333333, 0.0, 0.0001, 0.5, 0.999999, 1.0};

    private static PlaceResponse responseWithVibe(VibeVector vibe) {
        return new PlaceResponse(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                null, 37.5, 127.0, null, null, "CAFE", null, 5, vibe, 5L);
    }

    @Test
    void vibeSurvivesJsonRoundTripAcrossFullRealRange() {
        for (double v : SWEEP) {
            VibeVector vibe = new VibeVector(v, -v, v / 2, -v / 2);
            PlaceResponse response = responseWithVibe(vibe);
            assertThat(response.vibe()).as("v=%s", v).isEqualTo(vibe);

            String json = mapper.writeValueAsString(response);
            PlaceResponse parsed = mapper.readValue(json, PlaceResponse.class);

            assertThat(json).doesNotContain("\"zero\"");
            assertThat(parsed.vibe()).as("round-trip v=%s", v).isEqualTo(vibe);
        }
    }

    @Test
    void noReviewsSerializesVibeAsNull() {
        PlaceResponse response = new PlaceResponse(UUID.randomUUID(), null, 37.5, 127.0,
                null, null, null, null, 0, null, 0L);

        String json = mapper.writeValueAsString(response);
        assertThat(json).contains("\"vibe\":null");
    }
}
