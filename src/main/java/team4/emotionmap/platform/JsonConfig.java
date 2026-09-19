package team4.emotionmap.platform;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;

/** Reject duplicate keys and caller-supplied fields outside each request contract. */
@Configuration
public class JsonConfig {
    @Bean
    JsonMapperBuilderCustomizer strictRequestJson() {
        return builder -> builder
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    }
}
