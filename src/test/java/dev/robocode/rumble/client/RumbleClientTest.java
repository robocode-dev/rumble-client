package dev.robocode.rumble.client;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

class RumbleClientTest {
    @Test
    @Tag("Unit")
    void testUnitPositive_printsHelpWithoutConfiguration() throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        RumbleClient.run(new String[] {"--help"}, new PrintStream(bytes));

        assertTrue(bytes.toString().contains("rumble-client --validate-config [path]"));
        assertTrue(bytes.toString().contains("rumble-client --sync [path]"));
    }

    @Test
    @Tag("Unit")
    void testUnitNegative_rejectsUnknownCommand() {
        assertThrows(IllegalArgumentException.class, () -> RumbleClient.run(new String[] {"--submit"}, System.out));
    }
}
