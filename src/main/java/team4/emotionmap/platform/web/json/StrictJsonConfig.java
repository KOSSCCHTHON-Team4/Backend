package team4.emotionmap.platform.web.json;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 앱 전역 {@code JsonMapper} 에 {@link StrictJson} 규칙을 적용한다(요청 역직렬화·응답 직렬화 공통). */
@Configuration
public class StrictJsonConfig {

    @Bean
    JsonMapperBuilderCustomizer strictJsonMapperCustomizer() {
        return StrictJson::apply;
    }
}
