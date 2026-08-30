package dev.robocode.rumble.client;

import dev.robocode.tankroyale.runner.BattleException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunnerBattleExecutorIntegrationTest {
    private static final Path TANK_ROYALE_SAMPLE_BOTS = Path.of(System.getProperty("tankRoyaleSampleBotsJava"));

    @TempDir
    Path temporaryDirectory;

    @Test
    @Tag("RCL-005")
    void testRCL005_IntegrationPositive_realRunnerCompletesPinnedBattleAndRetainsReplay() throws Exception {
        final UUID battleId = UUID.fromString("ecb24b79-0c0c-497d-8b51-404e0edcc295");

        final RankedBattleRecord record = execution(battleId, 1).execute(selection(), cache(), snapshot(1),
                configuration(), "0.1.0");

        assertEquals(1, record.rounds());
        assertEquals(Set.of("Walls", "Spin Bot"), record.participants().stream()
                .map(RankedParticipant::name).collect(java.util.stream.Collectors.toSet()));
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("work/evidence").resolve(battleId + ".battle.gz")));
    }

    @Test
    @Tag("RCL-005")
    void testRCL005_IntegrationNegative_behaviorMismatchCreatesNoReplayEvidence() {
        assertThrows(BattleException.class, () -> execution(UUID.randomUUID(), 2).execute(selection(), cache(),
                snapshot(2), configuration(), "0.1.0"));

        assertFalse(Files.exists(temporaryDirectory.resolve("work/evidence")));
    }

    private RankedBattleExecution execution(final UUID battleId, final int behaviorVersion) {
        return new RankedBattleExecution(new RunnerBattleExecutor(),
                Clock.fixed(Instant.parse("2026-08-30T12:00:00Z"), ZoneOffset.UTC), () -> battleId);
    }

    private static BattleSelection selection() {
        return new BattleSelection(GameType.ONE_VS_ONE, 7L, List.of(walls(), spinBot()));
    }

    private static PreparedBotCache cache() {
        return new PreparedBotCache("a".repeat(40), Map.of(walls(), botDirectory("Walls"), spinBot(), botDirectory("SpinBot")));
    }

    private RumbleSnapshot snapshot(final int behaviorVersion) {
        final Map<String, CatalogBot> bots = Map.of(walls().displayName(), walls(), spinBot().displayName(), spinBot());
        final EnginePin engine = new EnginePin(behaviorVersion, "1.2.0", "image", Optional.empty(),
                Map.of(GameType.ONE_VS_ONE, new GameTypeSettings(1, 800, 600, 2)));
        return new RumbleSnapshot(java.net.URI.create("https://github.com/example/data"), "b".repeat(40), engine,
                new BotCatalog(java.net.URI.create("https://github.com/example/bots"), "a".repeat(40), bots),
                new ClientRegistration("alice", "alice-client"), Map.of());
    }

    private ClientConfiguration configuration() {
        return new ClientConfiguration(java.net.URI.create("https://github.com/example/bots"),
                java.net.URI.create("https://github.com/example/data"), Optional.of("alice-client"), Set.of(),
                Set.of(GameType.ONE_VS_ONE), 1, ClientMode.RANKED, temporaryDirectory.resolve("work"));
    }

    private static Path botDirectory(final String name) {
        final Path directory = TANK_ROYALE_SAMPLE_BOTS.resolve(name);
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException("Tank Royale Java sample bot is unavailable: " + directory);
        }
        return directory;
    }

    private static CatalogBot walls() {
        return new CatalogBot("Walls", "1.0", "JVM", "bots/java/Walls", "sha256:" + "a".repeat(64));
    }

    private static CatalogBot spinBot() {
        return new CatalogBot("Spin Bot", "1.0", "JVM", "bots/java/SpinBot", "sha256:" + "b".repeat(64));
    }
}
