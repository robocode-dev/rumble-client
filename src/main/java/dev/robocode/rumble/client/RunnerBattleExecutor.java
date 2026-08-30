package dev.robocode.rumble.client;

import dev.robocode.tankroyale.runner.BattleResults;
import dev.robocode.tankroyale.runner.BattleRunner;
import dev.robocode.tankroyale.runner.BattleSetup;
import dev.robocode.tankroyale.runner.BotEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Production adapter that executes one pinned battle through Battle Runner. */
final class RunnerBattleExecutor implements BattleExecutor {
    private static final int SOURCE_BOT_READY_TIMEOUT_MICROS = 10_000_000;

    @Override
    public CompletedBattle execute(final BattleSelection selection, final PreparedBotCache cache,
                                   final EnginePin engine, final GameTypeSettings settings,
                                   final Path recordingDirectory) throws IOException {
        final List<BotEntry> bots = selection.participants().stream()
                .map(cache.bots()::get)
                .map(path -> {
                    if (path == null) {
                        throw new IllegalArgumentException("Selection bot is absent from the prepared cache");
                    }
                    return BotEntry.of(path);
                })
                .toList();
        final BattleResults results;
        try (BattleRunner runner = BattleRunner.create(builder -> builder.embeddedServer()
                .enableRecording(recordingDirectory).requireBehaviorVersion(engine.behaviorVersion()))) {
            results = runner.runBattle(setup(selection.gameType(), settings), bots);
        }
        try (var files = Files.list(recordingDirectory)) {
            final List<Path> recordings = files.filter(path -> path.getFileName().toString().endsWith(".battle.gz"))
                    .toList();
            if (recordings.size() != 1) {
                throw new IOException("Battle Runner produced " + recordings.size() + " replay recordings");
            }
            return new CompletedBattle(results, recordings.get(0));
        }
    }

    private static BattleSetup setup(final GameType gameType, final GameTypeSettings settings) {
        return switch (gameType) {
            case ONE_VS_ONE -> BattleSetup.oneVsOne(builder -> configure(builder, settings));
            case TWIN_DUEL -> BattleSetup.twinDuel(builder -> configure(builder, settings));
            case MELEE -> BattleSetup.melee(builder -> configure(builder, settings));
        };
    }

    private static void configure(final BattleSetup.Builder builder, final GameTypeSettings settings) {
        builder.setNumberOfRounds(settings.rounds());
        builder.setArenaWidth(settings.arenaWidth());
        builder.setArenaHeight(settings.arenaHeight());
        builder.setReadyTimeoutMicros(SOURCE_BOT_READY_TIMEOUT_MICROS);
    }
}
