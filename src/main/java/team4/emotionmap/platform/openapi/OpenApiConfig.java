package team4.emotionmap.platform.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI(Swagger) 문서 메타데이터 + JWT Bearer 인증 스킴.
 * 명세 본체는 springdoc 이 컨트롤러/DTO 에서 자동 생성한다.
 *
 * Swagger UI 우측 상단 "Authorize" 에 로그인으로 받은 JWT 를 넣으면
 * 인증이 필요한 엔드포인트를 UI 에서 바로 테스트할 수 있다.
 *
 * 확인 경로 (기본):
 *   - Swagger UI : http://localhost:8080/swagger-ui.html
 *   - OpenAPI JSON: http://localhost:8080/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI emotionMapOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("감정지도 API")
                        .description("감정지도 백엔드 API 명세 (자동 생성). signup/login 외 엔드포인트는 JWT 필요.")
                        .version("v0.0.1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .name(BEARER)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
