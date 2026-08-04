package dev.robocode.rumble.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

class ClientConfigurationLoaderTest {
    private final ClientConfigurationLoader loader = new ClientConfigurationLoader();

    @Test
    @Tag("Unit")
    void testUnitPositive_loadsValidPracticeConfiguration() throws IOException {
        final Path configurationPath = writeConfiguration("practice", "registered-client");
        final ClientConfiguration configuration = loader.load(configurationPath);

        assertEquals(Optional.of("registered-client"), configuration.clientId());
        assertEquals(ClientMode.PRACTICE, configuration.mode());
        assertEquals(Set.of(GameType.ONE_VS_ONE, GameType.TWIN_DUEL, GameType.MELEE), configuration.gameTypes());
        assertEquals(configurationPath.getParent().resolve(".rumble-client").toAbsolutePath().normalize(),
                configuration.workDirectory());
    }

    @Test
    @Tag("Unit")
    void testUnitNegative_rejectsExampleClientId() throws IOException {
        final Path configurationPath = writeConfiguration("ranked", "replace-with-registered-client-id");

        assertThrows(IllegalArgumentException.class, () -> loader.load(configurationPath));
    }

    @Test
    @Tag("Unit")
    void testUnitPositive_allowsPracticeConfigurationWithoutClientId() throws IOException {
        final Path configurationPath = Files.createTempFile("rumble-client", ".json");
        Files.writeString(configurationPath, validConfiguration("practice", "registered-client")
                .replace("  \"clientId\": \"registered-client\",\n", ""));

        final ClientConfiguration configuration = loader.load(configurationPath);

        assertEquals(Optional.empty(), configuration.clientId());
    }

    @Test
    @Tag("Unit")
    void testUnitNegative_rejectsRankedConfigurationWithoutClientId() throws IOException {
        final Path configurationPath = Files.createTempFile("rumble-client", ".json");
        Files.writeString(configurationPath, validConfiguration("ranked", "registered-client")
                .replace("  \"clientId\": \"registered-client\",\n", ""));

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
    void testUnitNegative_rejectsRepositoryUrlQueryThatCouldCarryCredentials() throws IOException {
        final Path configurationPath = Files.createTempFile("rumble-client", ".json");
        Files.writeString(configurationPath, validConfiguration("ranked", "registered-client")
                .replace("https://github.com/robocode-dev/rumble-data",
                        "https://github.com/robocode-dev/rumble-data?token=secret"));

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

    @Test
    @Tag("Unit")
    void testUnitPositive_defaultsWorkDirectoryForExistingSchemaOneConfiguration() throws IOException {
        final Path configurationPath = Files.createTempFile("rumble-client", ".json");
        final String legacyConfiguration = validConfiguration("ranked", "registered-client")
                .replace(",\n  \"workDirectory\": \".rumble-client\"", "");
        assertFalse(legacyConfiguration.contains("workDirectory"));
        Files.writeString(configurationPath, legacyConfiguration);

        final ClientConfiguration configuration = loader.load(configurationPath);

        assertEquals(configurationPath.getParent().resolve(".rumble-client").toAbsolutePath().normalize(),
                configuration.workDirectory());
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
                  "mode": "%s",
                  "workDirectory": ".rumble-client"
                }
                """.formatted(clientId, mode);
    }
}
