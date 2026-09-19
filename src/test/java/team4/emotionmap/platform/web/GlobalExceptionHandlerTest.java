package team4.emotionmap.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import team4.emotionmap.account.dto.OnboardingRequest;
import team4.emotionmap.account.dto.PreferencesRequest;
import team4.emotionmap.contracts.dictionary.Atmospheres;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.media.ImageNotFoundException;
import team4.emotionmap.media.InvalidUploadException;
import team4.emotionmap.platform.web.json.StrictJson;
import tools.jackson.databind.json.JsonMapper;

/** Spring 컨텍스트·DB 없이 MockMvc standalone 으로 ApiError 형태·상태·헤더를 검증한다. */
class GlobalExceptionHandlerTest {

    record LoginLike(@JsonProperty(required = true) String email, @JsonProperty(required = true) String password) {
    }

    record AtmosphereBody(Atmospheres atmospheres) {
    }

    @RestController
    static class ProbeController {
        @PostMapping("/probe/login")
        String login(@RequestBody LoginLike body) {
            return "ok";
        }

        @PostMapping("/probe/atmospheres")
        String atmospheres(@RequestBody AtmosphereBody body) {
            return "ok";
        }

        @GetMapping("/probe/locked")
        String locked() {
            throw ContractError.retryAfter(ErrorCode.TOO_MANY_ATTEMPTS, 37);
        }

        @GetMapping("/probe/expired")
        String expired() {
            throw ContractError.of(ErrorCode.TOKEN_EXPIRED);
        }

        @GetMapping("/probe/credentials")
        String credentials() {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }

        @PostMapping("/probe/onboarding")
        String onboarding(@RequestBody OnboardingRequest body) {
            return "ok";
        }

        @PostMapping("/probe/preferences")
        String preferences(@RequestBody PreferencesRequest body) {
            return "ok";
        }

        @GetMapping("/probe/image")
        String image() {
            throw new ImageNotFoundException("storage path /private/uploads must not leak");
        }

        @PostMapping("/probe/image")
        String upload() {
            throw new InvalidUploadException("internal decoder detail must not leak");
        }

        @GetMapping("/probe/boom")
        String boom() {
            throw new IllegalStateException("db path /var/lib/secret must not leak");
        }

        @PostMapping("/probe/idem")
        String idem(@RequestHeader("Idempotency-Key") String key) {
            return key;
        }

        @GetMapping("/probe/memories/{id}")
        String byId(@PathVariable UUID id) {
            return id.toString();
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        JsonMapper mapper = StrictJson.mapper();
        mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler(new ApiErrorWriter(mapper)))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(mapper))
                .build();
    }

    @Test
    void contractErrorCarriesRetryAfterHeaderAndBody() throws Exception {
        MvcResult r = mvc.perform(get("/probe/locked"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "37"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(37))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.requestId").isString())
                .andReturn();
        assertThat(r.getResponse().getContentAsString()).contains("\"message\"");
    }

    @Test
    void unauthorizedIncludesWwwAuthenticate() throws Exception {
        mvc.perform(get("/probe/expired"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer")))
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void mainStatusExceptionUsesExactContractCodeAndHeaders() throws Exception {
        mvc.perform(get("/probe/credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void mainOnboardingAtmosphereFailureSurvivesJacksonWrapping() throws Exception {
        mvc.perform(post("/probe/onboarding").contentType(MediaType.APPLICATION_JSON).content("""
                        {"mailboxLat":37.5,"mailboxLng":127.0,"preferenceDescription":"",
                         "atmospheres":{"CROWD_LEVEL":1.0,"SPATIAL_FEEL":1,"COMPANY_FIT":1,"STAY_STYLE":-1}}
                        """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_ATMOSPHERES"));
    }

    @Test
    void mainImmutableFieldFailureSurvivesJacksonWrapping() throws Exception {
        mvc.perform(post("/probe/preferences").contentType(MediaType.APPLICATION_JSON).content("""
                        {"expectedPreferenceVersion":"1","preferenceDescription":"","mailboxLat":37.5,
                         "atmospheres":{"CROWD_LEVEL":1,"SPATIAL_FEEL":1,"COMPANY_FIT":1,"STAY_STYLE":-1}}
                        """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IMMUTABLE_FIELD"));
    }

    @Test
    void annotatedMainExceptionsKeepSafeClientErrors() throws Exception {
        MvcResult missing = mvc.perform(get("/probe/image"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andReturn();
        assertThat(missing.getResponse().getContentAsString()).doesNotContain("/private/uploads");
        MvcResult invalid = mvc.perform(post("/probe/image"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andReturn();
        assertThat(invalid.getResponse().getContentAsString()).doesNotContain("internal decoder");
    }

    @Test
    void loginExtraFieldIsInvalidRequest_A41() throws Exception {
        mvc.perform(post("/probe/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.invalid\",\"password\":\"x\",\"provider\":\"google\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("provider"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("UNKNOWN_FIELD"));
    }

    @Test
    void loginMissingOrWrongTypeIsInvalidRequest_A42() throws Exception {
        mvc.perform(post("/probe/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("REQUIRED"));
        mvc.perform(post("/probe/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.invalid\",\"password\":123}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void jsonSyntaxAndDuplicateKeys() throws Exception {
        mvc.perform(post("/probe/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JSON"));
        mvc.perform(post("/probe/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a\",\"email\":\"b\",\"password\":\"p\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_JSON_KEY"));
    }

    @Test
    void atmospheresTokenLevelRejectionIs422_A07() throws Exception {
        mvc.perform(post("/probe/atmospheres").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"atmospheres\":{\"CROWD_LEVEL\":1.0,\"SPATIAL_FEEL\":1,\"COMPANY_FIT\":1}}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_ATMOSPHERES"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='atmospheres.CROWD_LEVEL')].reason").value("INVALID_VALUE"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='atmospheres.STAY_STYLE')].reason").value("REQUIRED"));
    }

    @Test
    void missingIdempotencyKeyHasDedicatedCode() throws Exception {
        mvc.perform(post("/probe/idem"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void malformedUuidPathIsNotFoundToHideExistence() throws Exception {
        mvc.perform(get("/probe/memories/not-a-uuid"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void unexpectedExceptionNeverLeaksDetails() throws Exception {
        MvcResult r = mvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("SERVER_ERROR"))
                .andReturn();
        assertThat(r.getResponse().getContentAsString()).doesNotContain("/var/lib");
    }
}
