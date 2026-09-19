package team4.emotionmap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.embedding.* 설정 바인딩 (자리만 마련한 placeholder).
 *
 * 임베딩 모델/제공자는 아직 확정되지 않았다. 실제로 정해지면:
 *   1) provider / model / dimension 을 환경변수로 주입하고
 *   2) apiKey 는 EMBEDDING_API_KEY 환경변수로만 주입하며 (커밋 금지)
 *   3) Flyway 의 vector(N) 차원을 dimension 과 일치시킨다.
 *
 * @param enabled   임베딩 기능 사용 여부 (준비 전까지 false)
 * @param provider  임베딩 제공자 식별자 (예: voyage, openai, local ...)
 * @param model     임베딩 모델명
 * @param apiKey    제공자 API 키 (환경변수 주입, 로그/응답에 노출 금지)
 * @param dimension 임베딩 벡터 차원 (DB vector(N) 와 반드시 일치)
 */
@ConfigurationProperties(prefix = "app.embedding")
public record EmbeddingProperties(
        boolean enabled,
        String provider,
        String model,
        String apiKey,
        int dimension
) {
}
