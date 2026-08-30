package dev.robocode.rumble.client;

import dev.robocode.tankroyale.runner.BotResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Validates one completed ranked battle and binds its immutable result to retained replay evidence. */
final class RankedBattleExecution {
    private final BattleExecutor executor;
    private final Clock clock;
    private final Supplier<UUID> battleIds;

    RankedBattleExecution(final BattleExecutor executor, final Clock clock, final Supplier<UUID> battleIds) {
        this.executor = executor;
        this.clock = clock;
        this.battleIds = battleIds;
    }

    RankedBattleRecord execute(final BattleSelection selection, final PreparedBotCache cache,
                               final RumbleSnapshot snapshot, final ClientConfiguration configuration,
                               final String clientVersion) throws IOException {
        if (configuration.mode() != ClientMode.RANKED) {
            throw new IllegalArgumentException("Ranked battle execution requires ranked mode");
        }
        final GameTypeSettings settings = requireSettings(snapshot, selection.gameType());
        final UUID battleId = battleIds.get();
        final Path recordingDirectory = configuration.workDirectory().resolve("recordings").resolve(battleId.toString());
        Files.createDirectories(recordingDirectory);
        final CompletedBattle completed = executor.execute(selection, cache, snapshot.engine(), settings, recordingDirectory);
        validate(completed, selection, settings);
        final Path evidence = configuration.workDirectory().resolve("evidence").resolve(battleId + ".battle.gz");
        Files.createDirectories(evidence.getParent());
        Files.move(completed.replay(), evidence);
        return new RankedBattleRecord(battleId, clock.instant(),
                new ClientIdentity(snapshot.registration().clientId(), clientVersion),
                new EngineIdentity(snapshot.engine().behaviorVersion()), selection.gameType().contractName(),
                settings.rounds(), settings.arenaWidth(), settings.arenaHeight(),
                selection.randomSeed(),
                completed.results().getResults().stream().map(RankedBattleExecution::participant).toList(),
                sha256(evidence));
    }

    private static void validate(final CompletedBattle completed, final BattleSelection selection,
                                 final GameTypeSettings settings) {
        if (completed.results().getNumberOfRounds() != settings.rounds()) {
            throw new IllegalArgumentException("Battle completed " + completed.results().getNumberOfRounds()
                    + " rounds, but the engine pin requires " + settings.rounds());
        }
        if (!Files.isRegularFile(completed.replay())) {
            throw new IllegalArgumentException("Completed battle has no replay recording");
        }
        final Set<String> expected = selection.participants().stream()
                .map(bot -> identity(bot.name(), bot.version(), bot.isTeam())).collect(java.util.stream.Collectors.toSet());
        final Set<String> actual = new HashSet<>();
        for (final BotResult result : completed.results().getResults()) {
            actual.add(identity(result.getName(), result.getVersion(), result.isTeam()));
        }
        if (!actual.equals(expected) || actual.size() != completed.results().getResults().size()) {
            throw new IllegalArgumentException("Battle results do not match the ranked selection");
        }
    }

    private static GameTypeSettings requireSettings(final RumbleSnapshot snapshot, final GameType gameType) {
        final GameTypeSettings settings = snapshot.engine().gameTypes().get(gameType);
        if (settings == null) {
            throw new IllegalArgumentException("Engine pin has no settings for " + gameType.contractName());
        }
        return settings;
    }

    private static String identity(final String name, final String version, final boolean isTeam) {
        return name + "\n" + version + "\n" + isTeam;
    }

    private static RankedParticipant participant(final BotResult result) {
        return new RankedParticipant(result.getName(), result.getVersion(), result.isTeam(), result.getRank(),
                result.getTotalScore(), result.getSurvival(), result.getLastSurvivorBonus(), result.getBulletDamage(),
                result.getBulletKillBonus(), result.getRamDamage(), result.getRamKillBonus(), result.getFirstPlaces(),
                result.getSecondPlaces(), result.getThirdPlaces());
    }

    private static String sha256(final Path file) throws IOException {
        try {
            return "sha256:" + java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
