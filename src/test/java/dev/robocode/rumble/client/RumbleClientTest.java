package dev.robocode.rumble.client;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

class RumbleClientTest {
    @Test
    @Tag("Unit")
    void testUnitPositive_printsHelpWithoutConfiguration() throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        RumbleClient.run(new String[] {"--help"}, new PrintStream(bytes));

        assertTrue(bytes.toString().contains("rumble-client --validate-config [path]"));
        assertTrue(bytes.toString().contains("rumble-client --check-runtimes"));
        assertTrue(bytes.toString().contains("rumble-client --sync [path]"));
    }

    @Test
    @Tag("Unit")
    void testUnitNegative_rejectsUnknownCommand() {
        assertThrows(IllegalArgumentException.class, () -> RumbleClient.run(new String[] {"--submit"}, System.out));
    }

    @Test
    @Tag("Unit")
    void testUnitPositive_printsSuccessfulRuntimePreflight() throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final RequiredVersion version = new RequiredVersion(17, 0, false);

        RumbleClient.run(new String[] {"--check-runtimes"}, new PrintStream(bytes), () ->
                new RuntimeReport(List.of(RuntimeStatus.success("Java", version, "17.0.16"))));

        assertTrue(bytes.toString().contains("OK Java (required 17): 17.0.16"));
    }

    @Test
    @Tag("Unit")
    void testUnitNegative_failsRuntimePreflightWhenAnyRuntimeIsUnavailable() {
        final RequiredVersion version = new RequiredVersion(8, 0, false);

        assertThrows(IllegalArgumentException.class, () ->
                RumbleClient.run(new String[] {"--check-runtimes"}, System.out, () ->
                        new RuntimeReport(List.of(RuntimeStatus.failure(".NET SDK", version,
                                "command unavailable")))));
    }
}
