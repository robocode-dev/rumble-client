package dev.robocode.rumble.client;

import dev.robocode.tankroyale.runner.BattleResults;
import dev.robocode.tankroyale.runner.BotResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
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

class RankedBattleExecutionTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @Tag("RCL-005")
    void testRCL005_IntegrationPositive_completedPinnedBattleCreatesReplayBoundRecord() throws IOException {
        final UUID battleId = UUID.fromString("2a1e154d-9e16-4cd3-81c6-5e5d4092c731");
        final RankedBattleRecord record = execution(validExecutor(), battleId).execute(selection(), cache(), snapshot(),
                configuration(ClientMode.RANKED), "0.1.0");

        assertEquals(battleId, record.battleId());
        assertEquals("1v1", record.gameType());
        assertEquals(35, record.rounds());
        assertEquals(7L, record.selectionSeed());
        assertEquals(2, record.participants().size());
        assertTrue(record.replayHash().startsWith("sha256:"));
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("work/evidence").resolve(battleId + ".battle.gz")));
    }

    @Test
    @Tag("RCL-004")
    void testRCL004_IntegrationNegative_practiceModeCannotCreateRankedResult() {
        assertThrows(IllegalArgumentException.class, () -> execution(validExecutor(), UUID.randomUUID()).execute(selection(), cache(),
                snapshot(), configuration(ClientMode.PRACTICE), "0.1.0"));
        assertFalse(Files.exists(temporaryDirectory.resolve("work/evidence")));
    }

    @Test
    @Tag("RCL-004")
    void testRCL004_IntegrationPositive_rankedResultCanEnterOnlyTheRankedJournal() throws IOException {
        final RankedBattleRecord record = execution(validExecutor(), UUID.randomUUID()).execute(selection(), cache(),
                snapshot(), configuration(ClientMode.RANKED), "0.1.0");
        final RankedJournal journal = new RankedJournal(temporaryDirectory.resolve("work"));

        journal.append(record);

        assertEquals(List.of(record), journal.pending());
    }

    @Test
    @Tag("RCL-005")
    void testRCL005_IntegrationNegative_incompleteBattleCreatesNoEvidence() {
        final BattleExecutor incomplete = (selection, cache, engine, settings, recordings) -> {
            Files.createDirectories(recordings);
            final Path replay = recordings.resolve("partial.battle.gz");
            Files.writeString(replay, "partial");
            return new CompletedBattle(new BattleResults(34, results()), replay);
        };

        assertThrows(IllegalArgumentException.class, () -> execution(incomplete, UUID.randomUUID()).execute(selection(), cache(), snapshot(),
                configuration(ClientMode.RANKED), "0.1.0"));
        assertFalse(Files.exists(temporaryDirectory.resolve("work/evidence")));
    }

    private RankedBattleExecution execution(final BattleExecutor executor, final UUID battleId) {
        return new RankedBattleExecution(executor, Clock.fixed(Instant.parse("2026-08-30T12:00:00Z"), ZoneOffset.UTC),
                () -> battleId);
    }

    private BattleExecutor validExecutor() {
        return (selection, cache, engine, settings, recordings) -> {
            Files.createDirectories(recordings);
            final Path replay = recordings.resolve("game.battle.gz");
            Files.writeString(replay, "replay");
            return new CompletedBattle(new BattleResults(settings.rounds(), results()), replay);
        };
    }

    private static List<BotResult> results() {
        return List.of(result(1, "Alpha", 80, 35, 0), result(2, "Bravo", 20, 0, 35));
    }

    private static BotResult result(final int rank, final String name, final int score, final int firstPlaces,
                                    final int secondPlaces) {
        return new BotResult(rank, name, "1.0", false, rank, score, 0, 0, 0, 0, 0, 0, firstPlaces,
                secondPlaces, 0);
    }

    private static BattleSelection selection() {
        return new BattleSelection(GameType.ONE_VS_ONE, 7L, List.of(alpha(), bravo()));
    }

    private static PreparedBotCache cache() {
        return new PreparedBotCache("a".repeat(40), Map.of(alpha(), Path.of("alpha"), bravo(), Path.of("bravo")));
    }

    private static RumbleSnapshot snapshot() {
        final Map<String, CatalogBot> bots = Map.of(alpha().displayName(), alpha(), bravo().displayName(), bravo());
        final EnginePin engine = new EnginePin(1, "unreleased", "image", Optional.empty(),
                Map.of(GameType.ONE_VS_ONE, new GameTypeSettings(35, 800, 600, 2)));
        return new RumbleSnapshot(URI.create("https://github.com/example/data"), "b".repeat(40), engine,
                new BotCatalog(URI.create("https://github.com/example/bots"), "a".repeat(40), bots),
                new ClientRegistration("alice", "alice-client"), Map.of());
    }

    private ClientConfiguration configuration(final ClientMode mode) {
        return new ClientConfiguration(URI.create("https://github.com/example/bots"),
                URI.create("https://github.com/example/data"), mode == ClientMode.RANKED
                ? Optional.of("alice-client") : Optional.empty(), Set.of(), Set.of(GameType.ONE_VS_ONE), 1, mode,
                temporaryDirectory.resolve("work"));
    }

    private static CatalogBot alpha() {
        return new CatalogBot("Alpha", "1.0", "JVM", "bots/java/Alpha", "sha256:" + "a".repeat(64));
    }

    private static CatalogBot bravo() {
        return new CatalogBot("Bravo", "1.0", "JVM", "bots/java/Bravo", "sha256:" + "b".repeat(64));
    }
}
