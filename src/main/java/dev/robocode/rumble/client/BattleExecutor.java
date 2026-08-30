package dev.robocode.rumble.client;

import dev.robocode.tankroyale.runner.BattleResults;

import java.io.IOException;
import java.nio.file.Path;

/** Executes one prepared battle and returns its complete Runner result and recording. */
interface BattleExecutor {
    CompletedBattle execute(BattleSelection selection, PreparedBotCache cache, EnginePin engine,
                            GameTypeSettings settings, Path recordingDirectory) throws IOException;
}

record CompletedBattle(BattleResults results, Path replay) {
}
