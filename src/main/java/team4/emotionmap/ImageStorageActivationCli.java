package team4.emotionmap;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.validation.StrictValues;
import team4.emotionmap.media.ImageRootBinding;

/** Explicit empty-dataset activation, never legacy adoption or normal application startup. */
public final class ImageStorageActivationCli {

    private static final Pattern SAFE_SCHEMA = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private ImageStorageActivationCli() {
    }

    public static int run(String[] args) {
        Invocation invocation;
        Map<String, Object> properties;
        try {
            invocation = parse(args);
            properties = runtimeProperties(invocation, System.getenv());
        } catch (RuntimeException failure) {
            return failed(2);
        }
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(ImageStorageActivationBootstrap.class)
                .environment(isolatedEnvironment(properties))
                .web(WebApplicationType.NONE)
                .addCommandLineProperties(false)
                .logStartupInfo(false)
                .registerShutdownHook(false)
                .run()) {
            context.getBean(ImageRootBinding.class).activateEmpty(
                    invocation.datasetId(), invocation.database(), invocation.schema());
            System.out.println("Storage initialization completed.");
            return 0;
        } catch (ContractError failure) {
            return failed(failure.code() == ErrorCode.INVALID_REQUEST ? 2 : 1);
        } catch (IllegalArgumentException failure) {
            return failed(2);
        } catch (RuntimeException failure) {
            return failed(1);
        }
    }

    private static int failed(int code) {
        System.err.println("Storage initialization failed.");
        return code;
    }

    private static Invocation parse(String[] args) {
        String root = null;
        String dataset = null;
        String database = null;
        String schema = null;
        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            int separator = argument.indexOf('=');
            String option = separator < 0 ? argument : argument.substring(0, separator);
            String value;
            if (separator >= 0) {
                value = argument.substring(separator + 1);
            } else {
                if (++index >= args.length) {
                    throw new IllegalArgumentException("Missing activation argument");
                }
                value = args[index];
            }
            switch (option) {
                case "--root" -> root = assignOnce(root, value);
                case "--expected-dataset-id" -> dataset = assignOnce(dataset, value);
                case "--expected-database" -> database = assignOnce(database, value);
                case "--expected-schema" -> schema = assignOnce(schema, value);
                default -> throw new IllegalArgumentException("Unsupported activation argument");
            }
        }
        if (root == null || database == null || schema == null || !SAFE_SCHEMA.matcher(schema).matches()) {
            throw new IllegalArgumentException("Explicit activation arguments are required");
        }
        Path path = Path.of(root);
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("Activation requires an absolute root");
        }
        return new Invocation(path.normalize(), StrictValues.requireUuid(
                dataset, "expectedDatasetId", ErrorCode.INVALID_REQUEST), database, schema);
    }

    private static String assignOnce(String current, String value) {
        if (current != null || value == null || value.isBlank()) {
            throw new IllegalArgumentException("Invalid activation argument");
        }
        return value;
    }

    private static Map<String, Object> runtimeProperties(Invocation invocation, Map<String, String> environment) {
        String schema = requiredEnvironment(environment, "DB_SCHEMA");
        if (!schema.equals(invocation.schema())) {
            throw new IllegalArgumentException("Activation schema differs from the explicit connection schema");
        }
        Map<String, Object> properties = new HashMap<>();
        properties.put("app.storage.upload-dir", invocation.root().toString());
        properties.put("spring.datasource.url", requiredEnvironment(environment, "DB_URL"));
        properties.put("spring.datasource.username", requiredEnvironment(environment, "DB_USERNAME"));
        properties.put("spring.datasource.password", requiredEnvironment(environment, "DB_PASSWORD"));
        properties.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        properties.put("spring.datasource.hikari.schema", schema);
        properties.put("spring.jpa.properties.hibernate.default_schema", schema);
        properties.put("spring.config.location", "optional:classpath:/image-storage-activation-no-defaults.properties");
        properties.put("spring.main.banner-mode", "off");
        properties.put("spring.main.log-startup-info", "false");
        properties.put("spring.main.web-application-type", "none");
        properties.put("spring.flyway.enabled", "false");
        properties.put("spring.sql.init.mode", "never");
        properties.put("spring.data.jpa.repositories.enabled", "false");
        properties.put("spring.jpa.hibernate.ddl-auto", "validate");
        properties.put("spring.jpa.open-in-view", "false");
        properties.put("logging.level.root", "OFF");
        return properties;
    }

    private static String requiredEnvironment(Map<String, String> environment, String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Explicit activation environment is required");
        }
        return value;
    }

    private static StandardEnvironment isolatedEnvironment(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("imageStorageActivation", properties));
        return environment;
    }

    private record Invocation(Path root, UUID datasetId, String database, String schema) {
        @Override
        public String toString() {
            return "Invocation[REDACTED]";
        }
    }
}
