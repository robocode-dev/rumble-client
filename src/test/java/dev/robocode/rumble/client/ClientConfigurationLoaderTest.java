package dev.robocode.rumble.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class ClientConfigurationLoaderTest {
    private final ClientConfigurationLoader loader = new ClientConfigurationLoader();

    @Test
    @Tag("Unit")
    void testUnitPositive_loadsValidPracticeConfiguration() throws IOException {
        final ClientConfiguration configuration = loader.load(writeConfiguration("practice", "registered-client"));

        assertEquals("registered-client", configuration.clientId());
        assertEquals(ClientMode.PRACTICE, configuration.mode());
    }

    @Test
    @Tag("Unit")
    void testUnitNegative_rejectsExampleClientId() throws IOException {
        final Path configurationPath = writeConfiguration("ranked", "replace-with-registered-client-id");

        assertThrows(IllegalArgumentException.class, () -> loader.load(configurationPath));
    }

    @Test
    @Tag("Unit")
    void testUnitNegative_rejectsUnsupportedGameType() throws IOException {
        final Path configurationPath = Files.createTempFile("rumble-client", ".json");
        Files.writeString(configurationPath, validConfiguration("ranked", "registered-client")
                .replace("\"melee\"", "\"team\""));

        assertThrows(IllegalArgumentException.class, () -> loader.load(configurationPath));
    }

    @Test
    @Tag("Unit")
    void testUnitNegative_rejectsFractionalBattleCount() throws IOException {
        final Path configurationPath = Files.createTempFile("rumble-client", ".json");
        Files.writeString(configurationPath, validConfiguration("ranked", "registered-client")
                .replace("50", "1.5"));

        assertThrows(IllegalArgumentException.class, () -> loader.load(configurationPath));
    }

    @Test
    @Tag("Unit")
    void testUnitNegative_rejectsCredentialedRepositoryUrl() throws IOException {
        final Path configurationPath = Files.createTempFile("rumble-client", ".json");
        Files.writeString(configurationPath, validConfiguration("ranked", "registered-client")
                .replace("https://github.com/robocode-dev/rumble-bots", "https://credential@github.com/robocode-dev/rumble-bots"));

        assertThrows(IllegalArgumentException.class, () -> loader.load(configurationPath));
    }

    @Test
    @Tag("Unit")
    void testUnitNegative_rejectsEmptyGameTypes() throws IOException {
        final Path configurationPath = Files.createTempFile("rumble-client", ".json");
        Files.writeString(configurationPath, validConfiguration("ranked", "registered-client")
                .replace("[\"1v1\", \"twinduel\", \"melee\"]", "[]"));

        assertThrows(IllegalArgumentException.class, () -> loader.load(configurationPath));
    }

    private static Path writeConfiguration(final String mode, final String clientId) throws IOException {
        final Path configurationPath = Files.createTempFile("rumble-client", ".json");
        Files.writeString(configurationPath, validConfiguration(mode, clientId));
        return configurationPath;
    }

    private static String validConfiguration(final String mode, final String clientId) {
        return """
                {
                  "schemaVersion": 1,
                  "botsRepo": "https://github.com/robocode-dev/rumble-bots",
                  "dataRepo": "https://github.com/robocode-dev/rumble-data",
                  "clientId": "%s",
                  "myBots": [],
                  "gameTypes": ["1v1", "twinduel", "melee"],
                  "battlesPerSession": 50,
                  "mode": "%s"
                }
                """.formatted(clientId, mode);
    }
}
