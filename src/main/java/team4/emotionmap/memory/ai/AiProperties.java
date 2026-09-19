package team4.emotionmap.memory.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.ai.*} (C09). provider=mock 이면 실제 모델을 호출하지 않는다.
 * 모델·프롬프트 버전은 결과의 {@code AnalysisProvenance} 로 저장돼 재현 메타데이터가 된다.
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        String provider,
        Duration timeout,
        String analysisModel,
        String analysisPromptVersion,
        String moderationModel,
        String moderationPromptVersion,
        String baseUrl,
        String serviceToken
) {
    public static final String MOCK = "mock";
    /** 실제 AI FastAPI 서버(별도 프로세스)를 HTTP 로 호출하는 provider. */
    public static final String HTTP = "http";

    public boolean isMock() {
        return provider == null || provider.isBlank() || MOCK.equalsIgnoreCase(provider);
    }

    public boolean isHttp() {
        return HTTP.equalsIgnoreCase(provider);
    }
}
