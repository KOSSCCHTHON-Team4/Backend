package team4.emotionmap.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import team4.emotionmap.contracts.config.ServiceConfigSource;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.platform.web.ApiErrorWriter;
import team4.emotionmap.platform.web.GlobalExceptionHandler;
import team4.emotionmap.platform.web.json.StrictJson;
import tools.jackson.databind.json.JsonMapper;

/** A03: 응답이 API_SPEC 8.2~8.4 예시(JSON fixture)와 필드명·타입·순서까지 정확히 같은지 확인한다. */
class CatalogControllerTest {

    private static String fixture(String name) throws Exception {
        return Files.readString(Path.of("src/test/resources/fixtures/" + name), StandardCharsets.UTF_8);
    }

    private static MockMvc mvc(ServiceConfigSource source) {
        JsonMapper mapper = StrictJson.mapper();
        return MockMvcBuilders.standaloneSetup(new CatalogController(source))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiErrorWriter(mapper)))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(mapper))
                .build();
    }

    @Test
    void atmosphereAxesMatchSpecExactly() throws Exception {
        String body = mvc(() -> { throw new IllegalStateException("unused"); })
                .perform(get("/v1/atmosphere-axes")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JSONAssert.assertEquals(fixture("atmosphere-axes.v1.json"), body, JSONCompareMode.STRICT);
    }

    @Test
    void placeCategoriesMatchSpecExactly() throws Exception {
        String body = mvc(() -> { throw new IllegalStateException("unused"); })
                .perform(get("/v1/place-categories")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JSONAssert.assertEquals(fixture("place-categories.v1.json"), body, JSONCompareMode.STRICT);
    }

    @Test
    void configMatchesSpecMockExample_A44() throws Exception {
        ServiceConfigProvider provider = new ServiceConfigProvider(ServiceConfigProviderTest.mockProperties());
        String body = mvc(provider).perform(get("/v1/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.auth.mode").value("EMAIL_PASSWORD"))
                .andExpect(jsonPath("$.auth.refreshSupported").value(false))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JSONAssert.assertEquals(fixture("service-config.mock.json"), body, JSONCompareMode.STRICT);
    }

    @Test
    void missingConfigIs503NotSilentDefault() throws Exception {
        mvc(() -> { throw ContractError.of(ErrorCode.CONFIGURATION_UNAVAILABLE); })
                .perform(get("/v1/config"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CONFIGURATION_UNAVAILABLE"));
    }

    @Test
    void unversionedCatalogRoutesAreNotExposed() throws Exception {
        MockMvc mvc = mvc(() -> { throw new IllegalStateException("unused"); });
        for (String path : new String[]{"/config", "/atmosphere-axes", "/place-categories"}) {
            mvc.perform(get(path)).andExpect(status().isNotFound());
        }
    }
}
