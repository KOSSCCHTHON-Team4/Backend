package team4.emotionmap.account;

import java.io.Console;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import team4.emotionmap.contracts.account.AccountStatus;

/** Explicit, non-web entry point for creating one initial email-password account. */
public final class AccountProvisioningCli {

    private static final Pattern SAFE_SCHEMA = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final String FAILURE_MESSAGE = "Provisioning failed.";

    private AccountProvisioningCli() {
    }

    public static void main(String[] args) {
        int exitCode = run(args, System.console(), System.in, System.out, System.err, System.getenv());
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, Console console, InputStream standardInput, PrintStream output, PrintStream errors,
                   Map<String, String> environment) {
        char[] rawPassword = null;
        try {
            Invocation invocation = parseInvocation(args);
            RuntimeConfiguration configuration = RuntimeConfiguration.from(environment);
            rawPassword = readPassword(invocation, console, standardInput);
            try (ConfigurableApplicationContext context = new SpringApplicationBuilder(AccountProvisioningBootstrap.class)
                    .environment(provisioningEnvironment(configuration.properties()))
                    .web(WebApplicationType.NONE)
                    .addCommandLineProperties(false)
                    .logStartupInfo(false)
                    .registerShutdownHook(false)
                    .run()) {
                AccountProvisioningService.ProvisionedAccount provisioned = context
                        .getBean(AccountProvisioningService.class)
                        .provision(invocation.email(), rawPassword, invocation.accessStatus());
                output.println(provisioned.userId() + " " + provisioned.accessStatus().name());
                return 0;
            }
        } catch (IOError | IOException | RuntimeException e) {
            errors.println(FAILURE_MESSAGE);
            return 1;
        } finally {
            if (rawPassword != null) {
                Arrays.fill(rawPassword, '\0');
            }
        }
    }

    private static Invocation parseInvocation(String[] args) {
        if (args == null) {
            throw new IllegalArgumentException("arguments are required");
        }
        String email = null;
        AccountStatus accessStatus = null;
        boolean passwordStdin = false;
        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if ("--email".equals(argument)) {
                email = assignOnce(email, nextValue(args, ++index));
            } else if (argument.startsWith("--email=")) {
                email = assignOnce(email, argument.substring("--email=".length()));
            } else if ("--status".equals(argument)) {
                accessStatus = assignOnce(accessStatus, parseStatus(nextValue(args, ++index)));
            } else if (argument.startsWith("--status=")) {
                accessStatus = assignOnce(accessStatus, parseStatus(argument.substring("--status=".length())));
            } else if ("--password-stdin".equals(argument) && !passwordStdin) {
                passwordStdin = true;
            } else {
                throw new IllegalArgumentException("unsupported provisioning option");
            }
        }
        if (email == null || accessStatus == null) {
            throw new IllegalArgumentException("required provisioning option is missing");
        }
        return new Invocation(email, accessStatus, passwordStdin);
    }

    private static String nextValue(String[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("option value is missing");
        }
        return args[index];
    }

    private static String assignOnce(String current, String value) {
        if (current != null) {
            throw new IllegalArgumentException("duplicate provisioning option");
        }
        return value;
    }

    private static AccountStatus assignOnce(AccountStatus current, AccountStatus value) {
        if (current != null) {
            throw new IllegalArgumentException("duplicate provisioning option");
        }
        return value;
    }

    private static AccountStatus parseStatus(String value) {
        try {
            AccountStatus status = AccountStatus.valueOf(value);
            if (status == AccountStatus.ACTIVE || status == AccountStatus.PENDING) {
                return status;
            }
        } catch (IllegalArgumentException ignored) {
            // The caller receives the same generic failure as every other rejected invocation.
        }
        throw new IllegalArgumentException("unsupported initial status");
    }

    private static char[] readPassword(Invocation invocation, Console console, InputStream standardInput) throws IOException {
        char[] rawPassword;
        if (invocation.passwordStdin()) {
            rawPassword = readPasswordFromStandardInput(standardInput);
        } else {
            if (console == null) {
                throw new IllegalStateException("interactive console is unavailable");
            }
            rawPassword = console.readPassword();
        }
        if (!EmailPasswordInput.hasValidPassword(CharBuffer.wrap(rawPassword == null ? new char[0] : rawPassword))) {
            if (rawPassword != null) {
                Arrays.fill(rawPassword, '\0');
            }
            throw new IllegalArgumentException("initial password is invalid");
        }
        return rawPassword;
    }

    private static char[] readPasswordFromStandardInput(InputStream standardInput) throws IOException {
        byte[] encoded = standardInput.readNBytes(EmailPasswordInput.MAX_PASSWORD_UTF8_BYTES + 1);
        try {
            if (encoded.length == 0 || encoded.length > EmailPasswordInput.MAX_PASSWORD_UTF8_BYTES) {
                throw new IllegalArgumentException("initial password is invalid");
            }
            CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded));
            try {
                char[] password = new char[decoded.remaining()];
                decoded.get(password);
                return password;
            } finally {
                if (decoded.hasArray()) {
                    Arrays.fill(decoded.array(), '\0');
                }
            }
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("initial password is invalid", e);
        } finally {
            Arrays.fill(encoded, (byte) 0);
        }
    }

    private static StandardEnvironment provisioningEnvironment(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("accountProvisioning", properties));
        return environment;
    }

    private record Invocation(String email, AccountStatus accessStatus, boolean passwordStdin) {
    }

    private record RuntimeConfiguration(String dbUrl, String dbUsername, String dbPassword, String dbSchema,
                                        int bcryptStrength) {

        static RuntimeConfiguration from(Map<String, String> environment) {
            String schema = requireEnvironment(environment, "DB_SCHEMA");
            if (!SAFE_SCHEMA.matcher(schema).matches()) {
                throw new IllegalArgumentException("DB_SCHEMA is invalid");
            }
            return new RuntimeConfiguration(
                    requireEnvironment(environment, "DB_URL"),
                    requireEnvironment(environment, "DB_USERNAME"),
                    requireEnvironment(environment, "DB_PASSWORD"),
                    schema,
                    parseBcryptStrength(requireEnvironment(environment, "PASSWORD_BCRYPT_STRENGTH")));
        }

        Map<String, Object> properties() {
            Map<String, Object> properties = new HashMap<>();
            properties.put("app.security.password.bcrypt-strength", Integer.toString(bcryptStrength));
            properties.put("spring.config.location", "optional:classpath:/account-provisioning-no-defaults.properties");
            properties.put("spring.main.banner-mode", "off");
            properties.put("spring.main.log-startup-info", "false");
            properties.put("spring.main.web-application-type", "none");
            properties.put("spring.flyway.enabled", "false");
            properties.put("spring.sql.init.mode", "never");
            properties.put("spring.data.jpa.repositories.enabled", "false");
            properties.put("spring.jpa.hibernate.ddl-auto", "validate");
            properties.put("spring.jpa.open-in-view", "false");
            properties.put("spring.jpa.properties.hibernate.default_schema", dbSchema);
            properties.put("spring.datasource.hikari.schema", dbSchema);
            properties.put("spring.datasource.url", dbUrl);
            properties.put("spring.datasource.username", dbUsername);
            properties.put("spring.datasource.password", dbPassword);
            properties.put("logging.level.root", "OFF");
            return properties;
        }

        private static String requireEnvironment(Map<String, String> environment, String name) {
            String value = environment.get(name);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("required environment is missing");
            }
            return value;
        }

        private static int parseBcryptStrength(String value) {
            try {
                int strength = Integer.parseInt(value);
                if (strength >= 4 && strength <= 31) {
                    return strength;
                }
            } catch (NumberFormatException ignored) {
                // The caller receives the same generic failure as every other rejected invocation.
            }
            throw new IllegalArgumentException("bcrypt strength is invalid");
        }
    }
}
