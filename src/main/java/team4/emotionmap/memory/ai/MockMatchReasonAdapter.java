package team4.emotionmap.memory.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import team4.emotionmap.contracts.ai.MatchReasonPort;
import team4.emotionmap.contracts.ai.MatchReasonRequest;
import team4.emotionmap.contracts.ai.MatchReasonResult;

/** {@link MatchReasonPort} 가짜 구현: 카드 라벨과 리뷰 수로 고정 문구를 만든다. */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = AiProperties.MOCK, matchIfMissing = true)
public class MockMatchReasonAdapter implements MatchReasonPort {

    @Override
    public MatchReasonResult explain(MatchReasonRequest request) {
        String place = request.placeName() == null ? "이곳" : request.placeName();
        return MatchReasonResult.of(String.join(", ", request.cards()) + " 곳을 찾는 당신에게, "
                + place + "의 리뷰 " + request.reviews().size() + "개가 그런 분위기를 이야기해요.");
    }
}
