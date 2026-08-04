package dev.robocode.rumble.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Turns non-exclusive matchmaking advice into a reproducible ranked battle.
 */
final class RankedBattleSelector {
    private static final int GLOBAL_PRIORITY_CANDIDATE_LIMIT = 10;

    BattleSelection select(final RumbleSnapshot snapshot, final ClientConfiguration configuration,
                           final GameType gameType, final long randomSeed) {
        if (configuration.mode() != ClientMode.RANKED) {
            throw new IllegalArgumentException("Ranked battle selection requires ranked mode");
        }
        final GameTypeSettings settings = requireSettings(snapshot, gameType);
        final MatchAdvice advice = requireAdvice(snapshot, gameType);
        final List<CatalogBot> availableBots = snapshot.catalog().activeBots().values().stream()
                .sorted(Comparator.comparing(CatalogBot::displayName))
                .toList();
        if (availableBots.size() < settings.participants()) {
            throw new IllegalArgumentException("Game type " + gameType.contractName() + " requires "
                    + settings.participants() + " distinct active bots, but the catalog contains "
                    + availableBots.size());
        }

        final Random random = new Random(randomSeed);
        final List<CatalogBot> participants = new ArrayList<>(settings.participants());
        chooseAdviceAnchor(advice.priorityPairs(), configuration.myBots(), random).ifPresent(pair ->
                participants.addAll(pair.bots()));

        final Set<CatalogBot> selected = new HashSet<>(participants);
        final List<CatalogBot> remaining = new ArrayList<>(availableBots.stream()
                .filter(bot -> !selected.contains(bot))
                .toList());
        java.util.Collections.shuffle(remaining, random);
        participants.addAll(remaining.subList(0, settings.participants() - participants.size()));
        return new BattleSelection(gameType, randomSeed, participants);
    }

    private static Optional<PriorityPair> chooseAdviceAnchor(final List<PriorityPair> priorityPairs,
                                                             final Set<String> ownBots, final Random random) {
        final List<PriorityPair> ownBotPairs = priorityPairs.stream()
                .filter(pair -> pair.bots().stream().anyMatch(bot -> ownBots.contains(bot.name())))
                .toList();
        final List<PriorityPair> preferredPairs = ownBotPairs.isEmpty()
                ? priorityPairs.stream().limit(GLOBAL_PRIORITY_CANDIDATE_LIMIT).toList()
                : ownBotPairs;
        if (preferredPairs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(preferredPairs.get(random.nextInt(preferredPairs.size())));
    }

    private static GameTypeSettings requireSettings(final RumbleSnapshot snapshot, final GameType gameType) {
        final GameTypeSettings settings = snapshot.engine().gameTypes().get(gameType);
        if (settings == null) {
            throw new IllegalArgumentException("Engine pin has no settings for " + gameType.contractName());
        }
        return settings;
    }

    private static MatchAdvice requireAdvice(final RumbleSnapshot snapshot, final GameType gameType) {
        final MatchAdvice advice = snapshot.advice().get(gameType);
        if (advice == null) {
            throw new IllegalArgumentException("Snapshot has no matchmaking advice for " + gameType.contractName());
        }
        return advice;
    }
}
