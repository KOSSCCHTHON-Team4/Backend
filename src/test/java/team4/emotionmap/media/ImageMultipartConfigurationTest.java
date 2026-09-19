package team4.emotionmap.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.mockito.Mockito;
import team4.emotionmap.contracts.request.RequestCoordinator;
import team4.emotionmap.media.dto.ImageUploadResponse;
import team4.emotionmap.catalog.CatalogController;
import team4.emotionmap.catalog.ServiceConfigProperties;
import team4.emotionmap.catalog.ServiceConfigProvider;
import team4.emotionmap.contracts.config.AuthConfig;
import team4.emotionmap.contracts.config.ServiceConfig;
import team4.emotionmap.contracts.config.ServiceConfigSource;
import team4.emotionmap.contracts.config.ServiceLimits;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.geo.GeoPoint;
import team4.emotionmap.platform.security.AccountAccessGuard;
import team4.emotionmap.platform.security.CorsProperties;
import team4.emotionmap.platform.security.JwtProperties;
import team4.emotionmap.platform.security.JwtTokenProvider;
import team4.emotionmap.platform.security.SecurityConfig;
import team4.emotionmap.platform.web.ApiErrorWriter;
import team4.emotionmap.platform.web.GlobalExceptionHandler;
import team4.emotionmap.platform.web.json.StrictJson;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs real multipart request bodies through an embedded servlet server and the image controller.
 */
@SpringBootTest(
        classes = ImageMultipartConfigurationTest.NormalServletApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ImageMultipartConfigurationTest extends EmbeddedMultipartHttpAssertions {

    @Autowired
    private SwitchableServiceConfigSource serviceConfigSource;

    @BeforeEach
    void restoreAvailableConfiguration() {
        serviceConfigSource.makeAvailable();
    }

    @Test
    void securityAndConfigurationGateBothRunBeforeMultipartParsing() throws Exception {
        String token = serverToken();
        serviceConfigSource.makeUnavailable();
        byte[] oversized = new byte[TEST_IMAGE_MAX_BYTES + 1];

        assertApiError(postAnonymousImage(oversized), HttpStatus.UNAUTHORIZED.value(), ErrorCode.AUTH_REQUIRED);
        assertApiError(postAuthenticatedImage(oversized, token), HttpStatus.SERVICE_UNAVAILABLE.value(),
                ErrorCode.CONFIGURATION_UNAVAILABLE);
    }

    @Test
    void configuredMultipartLimitsRejectOversizeAndDeliverValidPartToController() throws Exception {
        String token = serverToken();
        assertApiError(postAuthenticatedImage(new byte[TEST_IMAGE_MAX_BYTES + 1], token),
                HttpStatus.PAYLOAD_TOO_LARGE.value(), ErrorCode.IMAGE_TOO_LARGE);

        HttpResponse<String> accepted = postAuthenticatedImage(new byte[]{1}, token);
        assertThat(accepted.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(accepted.body()).contains("\"sizeBytes\":1");
    }

    @SpringBootConfiguration
    @Import({EmbeddedMultipartHttpAssertions.ServletComponents.class, NormalValues.class})
    static class NormalServletApplication {
    }

    @Configuration(proxyBeanMethods = false)
    static class NormalValues {

        @Bean
        SwitchableServiceConfigSource serviceConfigSource() {
            return new SwitchableServiceConfigSource();
        }

        @Bean
        StorageProperties storageProperties() {
            return new StorageProperties("test-only-unused-storage", TEST_MULTIPART_OVERHEAD_BYTES,
                    Duration.ofMinutes(1), 10);
        }
    }
}

@SpringBootTest(
        classes = ImageMultipartConfigurationMissingServiceConfigTest.MissingConfigServletApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ImageMultipartConfigurationMissingServiceConfigTest extends EmbeddedMultipartHttpAssertions {

    @Test
    void missingServiceConfigRejectsTheConfigEndpointAndUploadBeforeMultipartParsing() throws Exception {
        String token = tokenIssuedAgainstKnownConfig();

        assertApiError(get("/v1/config", token), HttpStatus.SERVICE_UNAVAILABLE.value(),
                ErrorCode.CONFIGURATION_UNAVAILABLE);
        assertApiError(postAuthenticatedImage(new byte[TEST_IMAGE_MAX_BYTES + 1], token),
                HttpStatus.SERVICE_UNAVAILABLE.value(), ErrorCode.CONFIGURATION_UNAVAILABLE);
    }

    @SpringBootConfiguration
    @Import({EmbeddedMultipartHttpAssertions.ServletComponents.class, MissingConfigValues.class})
    static class MissingConfigServletApplication {
    }

    @Configuration(proxyBeanMethods = false)
    static class MissingConfigValues {

        @Bean
        ServiceConfigProvider serviceConfigSource() {
            return new ServiceConfigProvider(new ServiceConfigProperties(null, null, null, null, null));
        }

        @Bean
        StorageProperties storageProperties() {
            return new StorageProperties("test-only-unused-storage", TEST_MULTIPART_OVERHEAD_BYTES,
                    Duration.ofMinutes(1), 10);
        }
    }
}

@SpringBootTest(
        classes = ImageMultipartConfigurationMissingOverheadTest.MissingOverheadServletApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ImageMultipartConfigurationMissingOverheadTest extends EmbeddedMultipartHttpAssertions {

    @Test
    void missingRequestOverheadClosesOnlyImageUploadsWhileConfiguredCatalogStaysAvailable() throws Exception {
        String token = serverToken();

        assertThat(get("/v1/config", token).statusCode()).isEqualTo(HttpStatus.OK.value());
        assertApiError(postAuthenticatedImage(new byte[]{1}, token), HttpStatus.SERVICE_UNAVAILABLE.value(),
                ErrorCode.CONFIGURATION_UNAVAILABLE);
    }

    @SpringBootConfiguration
    @Import({EmbeddedMultipartHttpAssertions.ServletComponents.class, MissingOverheadValues.class})
    static class MissingOverheadServletApplication {
    }

    @Configuration(proxyBeanMethods = false)
    static class MissingOverheadValues {

        @Bean
        ServiceConfigSource serviceConfigSource() {
            return () -> AVAILABLE_CONFIG;
        }

        @Bean
        StorageProperties storageProperties() {
            return new StorageProperties("test-only-unused-storage", null, Duration.ofMinutes(1), 10);
        }
    }
}

@SpringBootTest(
        classes = ImageMultipartConfigurationOverflowingOverheadTest.OverflowingOverheadServletApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ImageMultipartConfigurationOverflowingOverheadTest extends EmbeddedMultipartHttpAssertions {

    @Test
    void overflowingRequestOverheadAlsoClosesOnlyImageUploads() throws Exception {
        String token = serverToken();

        assertThat(get("/v1/config", token).statusCode()).isEqualTo(HttpStatus.OK.value());
        assertApiError(postAuthenticatedImage(new byte[]{1}, token), HttpStatus.SERVICE_UNAVAILABLE.value(),
                ErrorCode.CONFIGURATION_UNAVAILABLE);
    }

    @SpringBootConfiguration
    @Import({EmbeddedMultipartHttpAssertions.ServletComponents.class, OverflowingOverheadValues.class})
    static class OverflowingOverheadServletApplication {
    }

    @Configuration(proxyBeanMethods = false)
    static class OverflowingOverheadValues {

        @Bean
        ServiceConfigSource serviceConfigSource() {
            return () -> AVAILABLE_CONFIG;
        }

        @Bean
        StorageProperties storageProperties() {
            return new StorageProperties("test-only-unused-storage", Long.MAX_VALUE,
                    Duration.ofMinutes(1), 10);
        }
    }
}

abstract class EmbeddedMultipartHttpAssertions {

    protected static final int TEST_IMAGE_MAX_BYTES = 64;
    protected static final long TEST_MULTIPART_OVERHEAD_BYTES = 8_192L;
    protected static final UUID TEST_USER_ID = UUID.fromString("6e36a0b2-29e5-4f3d-a695-51c5d1ec9d0c");
    private static final String TEST_JWT_SECRET = "test-only-jwt-signing-key-with-at-least-thirty-two-bytes";
    private static final Instant TEST_NOW = Instant.parse("2031-02-03T04:05:06Z");
    private static final Clock TEST_CLOCK = Clock.fixed(TEST_NOW, ZoneOffset.UTC);
    protected static final ServiceConfig AVAILABLE_CONFIG = new ServiceConfig(
            "multipart-http-test",
            1,
            new GeoPoint(1, 1),
            new ServiceLimits(1, 1, 1, TEST_IMAGE_MAX_BYTES, 1, 1, 1,
                    300, 300, 1, 1, 1, 1),
            new AuthConfig(300, 1, 1));

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    @Autowired
    protected JwtTokenProvider tokenProvider;

    @LocalServerPort
    protected int port;

    protected String serverToken() {
        return tokenProvider.createToken(TEST_USER_ID).token();
    }

    /** A server with missing configuration can still validate a token issued while its configuration was known. */
    protected static String tokenIssuedAgainstKnownConfig() {
        return new JwtTokenProvider(new JwtProperties(TEST_JWT_SECRET), () -> AVAILABLE_CONFIG, TEST_CLOCK)
                .createToken(TEST_USER_ID).token();
    }

    protected HttpResponse<String> postAnonymousImage(byte[] bytes) throws IOException, InterruptedException {
        return send(imageRequest(bytes).build());
    }

    protected HttpResponse<String> postAuthenticatedImage(byte[] bytes, String bearerToken)
            throws IOException, InterruptedException {
        return send(imageRequest(bytes).header("Authorization", "Bearer " + bearerToken).build());
    }

    protected HttpResponse<String> get(String path, String bearerToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(endpoint(path))
                .header("Authorization", "Bearer " + bearerToken)
                .GET()
                .build();
        return send(request);
    }

    protected static void assertApiError(HttpResponse<String> response, int expectedStatus, ErrorCode expectedCode) {
        assertThat(response.statusCode()).isEqualTo(expectedStatus);
        assertThat(response.body()).contains("\"code\":\"" + expectedCode.name() + "\"");
    }

    private HttpRequest.Builder imageRequest(byte[] bytes) {
        String boundary = "----emotionmap-" + UUID.randomUUID();
        return HttpRequest.newBuilder(endpoint("/v1/images"))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .header("Content-Type", MediaType.MULTIPART_FORM_DATA_VALUE + "; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody(bytes, boundary)));
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static byte[] multipartBody(byte[] fileBytes, String boundary) {
        byte[] prefix = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"fixture.jpg\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] body = new byte[prefix.length + fileBytes.length + suffix.length];
        System.arraycopy(prefix, 0, body, 0, prefix.length);
        System.arraycopy(fileBytes, 0, body, prefix.length, fileBytes.length);
        System.arraycopy(suffix, 0, body, prefix.length + fileBytes.length, suffix.length);
        return body;
    }

    private URI endpoint(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
            "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
    })
    @Import({JwtTokenProvider.class, SecurityConfig.class, ImageMultipartConfiguration.class, ImageController.class,
            ApiErrorWriter.class, GlobalExceptionHandler.class, CatalogController.class})
    static class ServletComponents {
        @Bean
        ImageUploadService imageUploadService() {
            ImageUploadService service = Mockito.mock(ImageUploadService.class);
            Mockito.when(service.upload(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(
                    new ImageUploadService.UploadResult(
                            new ImageUploadResponse(UUID.randomUUID(), TEST_NOW.plusSeconds(300),
                                    "image/jpeg", 1, 1, 1),
                            RequestCoordinator.CompletionKind.CREATED));
            return service;
        }

        @Bean
        Clock clock() {
            return TEST_CLOCK;
        }

        @Bean
        JwtProperties jwtProperties() {
            return new JwtProperties(TEST_JWT_SECRET);
        }

        @Bean
        CorsProperties corsProperties() {
            return new CorsProperties(List.of());
        }

        @Bean
        AccountAccessGuard accountAccessGuard() {
            return (userId, requestPath) -> "ROLE_USER";
        }

        @Bean
        JsonMapper jsonMapper() {
            return StrictJson.mapper();
        }
    }
}

final class SwitchableServiceConfigSource implements ServiceConfigSource {

    private volatile boolean available = true;

    @Override
    public ServiceConfig current() {
        if (!available) {
            throw ContractError.of(ErrorCode.CONFIGURATION_UNAVAILABLE);
        }
        return EmbeddedMultipartHttpAssertions.AVAILABLE_CONFIG;
    }

    void makeAvailable() {
        available = true;
    }

    void makeUnavailable() {
        available = false;
    }
}
