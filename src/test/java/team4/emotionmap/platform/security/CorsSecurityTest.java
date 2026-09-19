package team4.emotionmap.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import team4.emotionmap.platform.web.ApiErrorWriter;
import team4.emotionmap.platform.web.json.StrictJson;

/** Exercises the actual SecurityConfig filter chain against a test-only protected controller. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = CorsSecurityTest.SecurityTestConfiguration.class)
@WebAppConfiguration
class CorsSecurityTest {

    private static final String ALLOWED_ORIGIN = "https://app.example";
    private static final String UNLISTED_ORIGIN = "https://evil.example";
    private static final String PROBE_PATH = "/v1/cors-security-probe";
    private static final String ORIGIN = "Origin";
    private static final String REQUEST_METHOD = "Access-Control-Request-Method";
    private static final String REQUEST_HEADERS = "Access-Control-Request-Headers";
    private static final String ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    private static final String ALLOW_METHODS = "Access-Control-Allow-Methods";
    private static final String ALLOW_HEADERS = "Access-Control-Allow-Headers";
    private static final String ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";
    private static final String EXPOSE_HEADERS = "Access-Control-Expose-Headers";
    private static final List<String> ALLOWED_METHODS = List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS");
    private static final List<String> ALLOWED_HEADERS = List.of(
            "Authorization", "Content-Type", "Idempotency-Key", "X-Request-Id");
    private static final List<String> EXPOSED_HEADERS = List.of(
            "X-Request-Id", "Retry-After", "WWW-Authenticate");

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ProbeController probeController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        probeController.reset();
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .dispatchOptions(true)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowedPreflightUsesExactOriginMethodAndRequestedHeaders() throws Exception {
        MvcResult result = mockMvc.perform(options(PROBE_PATH)
                        .header(ORIGIN, ALLOWED_ORIGIN)
                        .header(REQUEST_METHOD, "POST")
                        .header(REQUEST_HEADERS, String.join(", ", ALLOWED_HEADERS)))
                .andExpect(status().isOk())
                .andExpect(header().string(ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().doesNotExist(ALLOW_CREDENTIALS))
                .andReturn();

        assertThat(commaSeparatedHeader(result, ALLOW_METHODS)).containsExactlyElementsOf(ALLOWED_METHODS);
        assertThat(commaSeparatedHeader(result, ALLOW_HEADERS)).containsExactlyElementsOf(ALLOWED_HEADERS);
        assertThat(probeController.invocationCount()).isZero();
    }

    @Test
    void allowedActualOriginExposesOnlyConfiguredOperationalHeadersAndStillRequiresAuthentication() throws Exception {
        MvcResult result = mockMvc.perform(get(PROBE_PATH).header(ORIGIN, ALLOWED_ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(header().string(ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().doesNotExist(ALLOW_CREDENTIALS))
                .andReturn();

        assertThat(commaSeparatedHeader(result, EXPOSE_HEADERS)).containsExactlyElementsOf(EXPOSED_HEADERS);
        assertThat(probeController.invocationCount()).isZero();
    }

    @Test
    void unlistedPreflightIsForbiddenBeforeItCanReachBusinessCode() throws Exception {
        mockMvc.perform(options(PROBE_PATH)
                        .header(ORIGIN, UNLISTED_ORIGIN)
                        .header(REQUEST_METHOD, "POST")
                        .header(REQUEST_HEADERS, String.join(", ", ALLOWED_HEADERS)))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(ALLOW_ORIGIN));

        assertThat(probeController.invocationCount()).isZero();
    }

    @Test
    void ordinaryOptionsWithoutOriginIsAuthRequiredInsteadOfBeingGloballyPermitted() throws Exception {
        mockMvc.perform(options(PROBE_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        assertThat(probeController.invocationCount()).isZero();
    }

    @Test
    void protectedGetAndPostBothRequireAuthentication() throws Exception {
        mockMvc.perform(get(PROBE_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        mockMvc.perform(post(PROBE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        assertThat(probeController.invocationCount()).isZero();
    }

    @Test
    void unlistedActualOriginIsForbiddenBeforeItCanReachBusinessCode() throws Exception {
        mockMvc.perform(get(PROBE_PATH).header(ORIGIN, UNLISTED_ORIGIN))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(ALLOW_ORIGIN));

        assertThat(probeController.invocationCount()).isZero();
    }

    private static List<String> commaSeparatedHeader(MvcResult result, String name) {
        String value = result.getResponse().getHeader(name);
        assertThat(value).as("response header %s", name).isNotNull();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .toList();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class SecurityTestConfiguration {

        @Bean
        CorsProperties corsProperties() {
            return new CorsProperties(List.of(ALLOWED_ORIGIN));
        }

        @Bean
        JwtTokenProvider tokenProvider() {
            return mock(JwtTokenProvider.class);
        }

        @Bean
        AccountAccessGuard accountAccessGuard() {
            return (userId, requestPath) -> "ROLE_USER";
        }

        @Bean
        ApiErrorWriter apiErrorWriter() {
            return new ApiErrorWriter(StrictJson.mapper());
        }

        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }

    @RestController
    @RequestMapping(PROBE_PATH)
    static class ProbeController {

        private final AtomicInteger invocations = new AtomicInteger();

        @GetMapping
        String get() {
            return invoked();
        }

        @PostMapping
        String post() {
            return invoked();
        }

        @RequestMapping(method = RequestMethod.OPTIONS)
        String options() {
            return invoked();
        }

        void reset() {
            invocations.set(0);
        }

        int invocationCount() {
            return invocations.get();
        }

        private String invoked() {
            invocations.incrementAndGet();
            return "business";
        }
    }
}
