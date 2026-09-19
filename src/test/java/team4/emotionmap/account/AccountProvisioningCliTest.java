package team4.emotionmap.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AccountProvisioningCliTest {

    @Test
    void consoleIoErrorUsesTheSameGenericFailureBoundaryAsOtherRejectedInvocations() {
        String sensitiveCause = "/private/synthetic-terminal-secret";
        Console console = mock(Console.class);
        when(console.readPassword()).thenThrow(new IOError(new IOException(sensitiveCause)));

        RunResult consoleFailure = run(
                new String[]{"--email", "user@example.com", "--status", "ACTIVE"},
                console,
                runtimeEnvironment());
        RunResult ordinaryFailure = run(new String[]{"--unsupported"}, null, runtimeEnvironment());

        assertThat(consoleFailure.exitCode()).isEqualTo(1);
        assertThat(consoleFailure.stdout()).isEmpty();
        assertThat(consoleFailure.stderr()).isEqualTo(ordinaryFailure.stderr());
        assertThat(consoleFailure.stderr()).doesNotContain(sensitiveCause, IOError.class.getName());
    }

    private static RunResult run(String[] args, Console console, Map<String, String> environment) {
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(outputBytes, true, StandardCharsets.UTF_8);
             PrintStream errors = new PrintStream(errorBytes, true, StandardCharsets.UTF_8)) {
            int exitCode = AccountProvisioningCli.run(
                    args, console, InputStream.nullInputStream(), output, errors, environment);
            return new RunResult(
                    exitCode,
                    outputBytes.toString(StandardCharsets.UTF_8),
                    errorBytes.toString(StandardCharsets.UTF_8));
        }
    }

    private static Map<String, String> runtimeEnvironment() {
        return Map.of(
                "DB_URL", "jdbc:postgresql://127.0.0.1:1/synthetic",
                "DB_USERNAME", "synthetic-user",
                "DB_PASSWORD", "synthetic-password",
                "DB_SCHEMA", "public",
                "PASSWORD_BCRYPT_STRENGTH", "4");
    }

    private record RunResult(int exitCode, String stdout, String stderr) {
    }
}
