package team4.emotionmap.platform.openapi;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import team4.emotionmap.contracts.dictionary.AtmosphereAxis;

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

    private static final BigDecimal AXIS_MINIMUM = new BigDecimal("-1.0");
    private static final BigDecimal AXIS_MAXIMUM = new BigDecimal("1.0");
    private static final BigDecimal AXIS_FRACTION_EXAMPLE = new BigDecimal("0.25");

    private static final List<String> AXIS_CODES = AtmosphereAxis.ordered().stream()
            .map(AtmosphereAxis::name)
            .toList();

    @Bean
    public OpenAPI emotionMapOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("감정지도 API")
                        .description("현재 구현된 /v1 업무 API. /v1/auth/login 외에는 JWT와 계정 접근 권한 필요. 전체 제품 계약은 docs/openapi.yaml 참고.")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .name(BEARER)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    @Bean
    public OpenApiCustomizer atmosphereAxisSchemas() {
        return openApi -> {
            Components components = openApi.getComponents();
            if (components == null) {
                components = new Components();
                openApi.setComponents(components);
            }
            components.addSchemas("Atmospheres", atmosphereSchema(false))
                    .addSchemas("AnalyzedAtmospheres", atmosphereSchema(true));
        };
    }

    private static ObjectSchema atmosphereSchema(boolean nullable) {
        ObjectSchema schema = new ObjectSchema();
        schema.setDescription(nullable
                ? "AI 분석의 필수 4축. 키는 항상 있고 null은 미결이며 0을 포함한 숫자는 알려진 값이다."
                : "최종 설정·경험의 필수 4축. 0과 소수를 포함한 유한 [-1, 1] binary64 값이다.");
        schema.setAdditionalProperties(false);
        for (AtmosphereAxis axis : AtmosphereAxis.ordered()) {
            schema.addProperties(axis.name(), axisValueSchema(axis, nullable));
        }
        schema.setRequired(AXIS_CODES);
        return schema;
    }

    private static Schema<?> axisValueSchema(AtmosphereAxis axis, boolean nullable) {
        NumberSchema numberSchema = new NumberSchema();
        numberSchema.setType("number");
        numberSchema.setFormat("double");
        numberSchema.setMinimum(AXIS_MINIMUM);
        numberSchema.setMaximum(AXIS_MAXIMUM);
        numberSchema.setDescription("유한한 [-1, 1] 연속 binary64 축 값.");
        numberSchema.setExample(axisExample(axis));
        if (!nullable) {
            return numberSchema;
        }

        ComposedSchema nullableSchema = new ComposedSchema();
        nullableSchema.addAnyOfItem(numberSchema);
        nullableSchema.addAnyOfItem(new Schema<>().type("null"));
        return nullableSchema;
    }

    private static BigDecimal axisExample(AtmosphereAxis axis) {
        return switch (axis) {
            case CROWD_LEVEL -> BigDecimal.ZERO;
            case SPATIAL_FEEL -> AXIS_FRACTION_EXAMPLE;
            case COMPANY_FIT -> AXIS_MINIMUM;
            case STAY_STYLE -> AXIS_MAXIMUM;
        };
    }
}
