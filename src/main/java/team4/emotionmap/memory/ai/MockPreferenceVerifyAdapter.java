package team4.emotionmap.memory.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import team4.emotionmap.contracts.ai.PreferenceVerifyPort;
import team4.emotionmap.contracts.ai.VerifyRequest;
import team4.emotionmap.contracts.ai.VerifyResult;

/**
 * {@link PreferenceVerifyPort} 가짜 구현. 취향 문장의 2글자 이상 어절이 리뷰에 하나라도 등장하면 fit.
 * {@code [[VERIFY_FAIL]]} 마커면 상류 실패. 실제 모델이 아니다.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = AiProperties.MOCK, matchIfMissing = true)
public class MockPreferenceVerifyAdapter implements PreferenceVerifyPort {

    static final String FAIL_MARKER = "[[VERIFY_FAIL]]";

    @Override
    public VerifyResult verify(VerifyRequest request) {
        if (request.preferenceText().contains(FAIL_MARKER)) {
            return VerifyResult.failed("MOCK_UPSTREAM_FAILURE");
        }
        String joined = String.join(" ", request.reviews()).toLowerCase(Locale.ROOT);
        List<String> evidence = new ArrayList<>();
        for (String word : request.preferenceText().toLowerCase(Locale.ROOT).split("\\s+")) {
            String w = word.replaceAll("[^\\p{L}\\p{N}]", "");
            if (w.length() >= 2 && joined.contains(w)) {
                evidence.add(w);
            }
        }
        boolean fit = !evidence.isEmpty();
        return VerifyResult.of(fit, fit ? 0.8 : 0.3,
                fit ? "리뷰에서 '" + evidence.getFirst() + "'와 맞닿는 표현을 찾았어요." : "취향 문장과 맞는 근거를 찾지 못했어요.",
                evidence);
    }
}
