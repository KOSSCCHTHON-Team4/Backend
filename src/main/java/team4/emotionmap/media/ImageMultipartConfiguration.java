package team4.emotionmap.media;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.servlet.MultipartConfigFactory;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;
import team4.emotionmap.contracts.config.ServiceConfigSource;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.platform.web.ApiErrorWriter;

@Configuration(proxyBeanMethods = false)
public class ImageMultipartConfiguration {

    private static final long DENY_ALL_BYTES = 1L;

    private final ServiceConfigSource serviceConfigSource;
    private final StorageProperties storageProperties;
    private final ApiErrorWriter apiErrorWriter;

    public ImageMultipartConfiguration(ServiceConfigSource serviceConfigSource, StorageProperties storageProperties,
                                       ApiErrorWriter apiErrorWriter) {
        this.serviceConfigSource = serviceConfigSource;
        this.storageProperties = storageProperties;
        this.apiErrorWriter = apiErrorWriter;
    }

    @Bean
    public MultipartConfigElement multipartConfigElement(MultipartProperties multipartProperties) {
        MultipartLimits limits = startupMultipartLimits();
        if (limits == null) {
            // The gate rejects every affected request before multipart parsing reaches this sentinel.
            return createMultipartConfig(multipartProperties, DENY_ALL_BYTES, DENY_ALL_BYTES);
        }
        return createMultipartConfig(multipartProperties, limits.maxFileBytes(), limits.maxRequestBytes());
    }

    @Bean
    public FilterRegistrationBean<ImageUploadConfigurationGate> imageUploadConfigurationGate(
            SecurityFilterProperties securityFilterProperties) {
        FilterRegistrationBean<ImageUploadConfigurationGate> registration =
                new FilterRegistrationBean<>(new ImageUploadConfigurationGate());
        registration.addUrlPatterns("/v1/images");
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.setOrder(orderAfter(securityFilterProperties.getOrder()));
        return registration;
    }

    private MultipartLimits startupMultipartLimits() {
        try {
            long maxFileBytes = serviceConfigSource.current().limits().imageMaxBytes();
            return multipartLimits(maxFileBytes);
        } catch (ContractError error) {
            if (error.code() == ErrorCode.CONFIGURATION_UNAVAILABLE) {
                return null;
            }
            throw error;
        }
    }

    private MultipartLimits multipartLimits(long maxFileBytes) {
        Long overheadBytes = storageProperties.multipartRequestOverheadBytes();
        if (maxFileBytes < 1 || overheadBytes == null || overheadBytes < 1) {
            return null;
        }
        try {
            // The controller's bounded original-byte read is an int-sized B + 1 operation.
            Math.toIntExact(Math.addExact(maxFileBytes, 1L));
            return new MultipartLimits(maxFileBytes, Math.addExact(maxFileBytes, overheadBytes));
        } catch (ArithmeticException ignored) {
            return null;
        }
    }

    private boolean hasUsableTransport(long maxFileBytes) {
        Long overheadBytes = storageProperties.multipartRequestOverheadBytes();
        if (maxFileBytes < 1 || overheadBytes == null || overheadBytes < 1) {
            return false;
        }
        try {
            Math.toIntExact(Math.addExact(maxFileBytes, 1L));
            Math.addExact(maxFileBytes, overheadBytes);
            return true;
        } catch (ArithmeticException ignored) {
            return false;
        }
    }

    private static MultipartConfigElement createMultipartConfig(MultipartProperties properties,
                                                                 long maxFileBytes, long maxRequestBytes) {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        if (StringUtils.hasText(properties.getLocation())) {
            factory.setLocation(properties.getLocation());
        }
        factory.setFileSizeThreshold(properties.getFileSizeThreshold());
        factory.setMaxFileSize(DataSize.ofBytes(maxFileBytes));
        factory.setMaxRequestSize(DataSize.ofBytes(maxRequestBytes));
        return factory.createMultipartConfig();
    }

    private static int orderAfter(int securityFilterOrder) {
        try {
            return Math.addExact(securityFilterOrder, 1);
        } catch (ArithmeticException ignored) {
            throw new IllegalStateException("Cannot register the image configuration gate after security");
        }
    }

    private final class ImageUploadConfigurationGate extends OncePerRequestFilter {

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            return !"POST".equals(request.getMethod());
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            ContractError configurationError = uploadConfigurationError();
            if (configurationError != null) {
                apiErrorWriter.write(response, request, configurationError);
                return;
            }
            filterChain.doFilter(request, response);
        }

        private ContractError uploadConfigurationError() {
            try {
                long maxFileBytes = serviceConfigSource.current().limits().imageMaxBytes();
                if (hasUsableTransport(maxFileBytes)) {
                    return null;
                }
            } catch (ContractError error) {
                if (error.code() == ErrorCode.CONFIGURATION_UNAVAILABLE) {
                    return error;
                }
                throw error;
            }
            return ContractError.of(ErrorCode.CONFIGURATION_UNAVAILABLE);
        }
    }

    private record MultipartLimits(long maxFileBytes, long maxRequestBytes) {
    }
}
