package team4.emotionmap.memory.ai;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * provider=http 일 때만 AI FastAPI 서버 호출용 {@link RestClient} 를 등록한다.
 *
 * <p>base-url 은 {@code app.ai.base-url}(예: http://localhost:8000). {@code app.ai.service-token} 이 있으면
 * 모든 요청에 {@code X-AI-Token} 헤더를 붙인다(AI 서버의 AI_SERVICE_TOKEN 과 공유 비밀).
 * Claude API 키는 여기 오지 않는다 — 백엔드는 AI 서버만 부르고, 키는 AI 서버 .env 에만 있다.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = AiProperties.HTTP)
public class AiHttpClientConfig {

    @Bean
    public RestClient aiRestClient(AiProperties properties) {
        String baseUrl = properties.baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("app.ai.base-url must be set when app.ai.provider=http");
        }
        // SimpleClientHttpRequestFactory 를 명시한다. RestClient 기본(JDK HttpClient) 팩토리는 이 환경에서
        // POST 본문을 실어 보내지 못해 AI 서버가 422(body missing)를 반환했다. Simple 팩토리는 정상 전송된다.
        Duration timeout = properties.timeout() == null ? Duration.ofSeconds(10) : properties.timeout();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.max(2_000L, timeout.toMillis())));
        // 안전 검사·동률은 더 오래 걸릴 수 있어 읽기 타임아웃은 넉넉히(최소 20s) 둔다.
        factory.setReadTimeout(Duration.ofMillis(Math.max(20_000L, timeout.toMillis())));

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl.replaceAll("/+$", ""))
                .requestFactory(factory);
        String token = properties.serviceToken();
        if (token != null && !token.isBlank()) {
            builder = builder.defaultHeader("X-AI-Token", token);
        }
        return builder.build();
    }
}
