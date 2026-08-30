package dev.robocode.rumble.client;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankedBattleSelectorTest {
    private static final int GLOBAL_PRIORITY_CANDIDATE_LIMIT = 10;
    private static final long RANDOM_SEED = 482193L;

    @Test
    @Tag("RCL-010")
    void testRCL010_UnitPositive_prefersOwnEntryAdviceAndSelectsEachPinnedParticipantCount() {
        final RumbleSnapshot snapshot = snapshot(12, true);
        final ClientConfiguration configuration = configuration(Set.of("Bot 03", "Team 02"));
        final RankedBattleSelector selector = new RankedBattleSelector();

        for (final GameType gameType : GameType.values()) {
            final BattleSelection selection = selector.select(snapshot, configuration, gameType, RANDOM_SEED);

            final int expectedEntries = snapshot.engine().gameTypes().get(gameType).participants()
                    / gameType.teamSize();
            assertEquals(expectedEntries, selection.participants().size());
            assertEquals(selection.participants().size(), Set.copyOf(selection.participants()).size());
            final List<String> expectedAdvice = gameType.isTeamGame()
                    ? List.of("Team 02", "Team 03") : List.of("Bot 03", "Bot 04");
            assertTrue(selection.participants().stream().map(CatalogBot::name).toList()
                    .containsAll(expectedAdvice));
            assertEquals(snapshot.engine().gameTypes().get(gameType).participants(),
                    selection.participants().stream().mapToInt(CatalogBot::expandedParticipantCount).sum());
            assertEquals(RANDOM_SEED, selection.randomSeed());
        }
    }

    @Test
    @Tag("RCL-010")
    void testRCL010_UnitPositive_selectsGlobalAdviceOnlyFromTheHighPriorityWindow() {
        final RumbleSnapshot baseSnapshot = snapshot(12, false);
        final List<CatalogBot> bots = baseSnapshot.catalog().activeBots().values().stream()
                .filter(bot -> !bot.isTeam())
                .sorted(Comparator.comparing(CatalogBot::displayName))
                .toList();
        final List<PriorityPair> pairs = IntStream.range(1, bots.size())
                .mapToObj(index -> new PriorityPair(List.of(bots.get(0), bots.get(index)), index, "under-sampled"))
                .toList();
        final MatchAdvice advice = new MatchAdvice(GameType.ONE_VS_ONE, "a".repeat(64), 12, pairs);
        final RumbleSnapshot snapshot = new RumbleSnapshot(baseSnapshot.canonicalDataRepository(),
                baseSnapshot.dataRevision(), baseSnapshot.engine(), baseSnapshot.catalog(),
                baseSnapshot.registration(), Map.of(GameType.ONE_VS_ONE, advice));
        final Set<Set<CatalogBot>> highPriorityPairs = pairs.stream().limit(GLOBAL_PRIORITY_CANDIDATE_LIMIT)
                .map(pair -> Set.copyOf(pair.bots()))
                .collect(Collectors.toSet());
        final RankedBattleSelector selector = new RankedBattleSelector();

        for (long seed = 0; seed < 100; seed++) {
            final BattleSelection selection = selector.select(snapshot, configuration(Set.of()),
                    GameType.ONE_VS_ONE, seed);

            assertTrue(highPriorityPairs.contains(Set.copyOf(selection.participants())));
        }
    }

    @Test
    @Tag("RCL-010")
    void testRCL010_UnitPositive_usesSeededCatalogFallbackWhenAdviceIsEmpty() {
        final RumbleSnapshot snapshot = snapshot(12, false);
        final RankedBattleSelector selector = new RankedBattleSelector();

        final BattleSelection first = selector.select(snapshot, configuration(Set.of()), GameType.MELEE, RANDOM_SEED);
        final BattleSelection repeated = selector.select(snapshot, configuration(Set.of()), GameType.MELEE, RANDOM_SEED);

        assertEquals(first, repeated);
        assertEquals(10, first.participants().size());
    }

    @Test
    @Tag("RCL-011")
    void testRCL011_UnitNegative_rejectsTwinDuelWhenEveryTeamSharesAMemberBot() {
        final RumbleSnapshot base = snapshot(12, false);
        final Map<String, CatalogBot> bots = new LinkedHashMap<>();
        base.catalog().activeBots().values().stream().filter(bot -> !bot.isTeam())
                .forEach(bot -> bots.put(bot.displayName(), bot));
        final List<CatalogBot> individuals = List.copyOf(bots.values());
        for (int index = 1; index < individuals.size(); index++) {
            final String name = "Overlap %02d".formatted(index);
            final CatalogBot team = new CatalogBot(name, "1.0", "JVM", "bots/java/" + name,
                    "sha256:" + "%064x".formatted(100 + index),
                    List.of(individuals.get(0).displayName(), individuals.get(index).displayName()));
            bots.put(team.displayName(), team);
        }
        final RumbleSnapshot snapshot = new RumbleSnapshot(base.canonicalDataRepository(), base.dataRevision(),
                base.engine(), new BotCatalog(base.catalog().source(), base.catalog().sourceCommit(), bots),
                base.registration(), base.advice());

        final IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new RankedBattleSelector()
                        .select(snapshot, configuration(Set.of()), GameType.TWIN_DUEL, RANDOM_SEED));

        assertTrue(failure.getMessage().contains("share no member bot"), failure.getMessage());
    }

    @Test
    @Tag("RCL-011")
    void testRCL011_UnitPositive_selectsTwinDuelTeamsWithDisjointMembers() {
        final BattleSelection selection = new RankedBattleSelector().select(snapshot(12, false), configuration(Set.of()),
                GameType.TWIN_DUEL, RANDOM_SEED);

        final Set<String> members = selection.participants().stream().flatMap(team -> team.teamMembers().stream())
                .collect(Collectors.toSet());

        assertEquals(4, members.size());
    }

    @Test
    @Tag("RCL-010")
    void testRCL010_UnitNegative_rejectsASelectionWithoutEnoughDistinctActiveBots() {
        final RumbleSnapshot snapshot = snapshot(9, false);

        assertThrows(IllegalArgumentException.class, () -> new RankedBattleSelector()
                .select(snapshot, configuration(Set.of()), GameType.MELEE, RANDOM_SEED));
    }

    private static RumbleSnapshot snapshot(final int botCount, final boolean withAdvice) {
        final Map<String, CatalogBot> bots = new LinkedHashMap<>();
        for (int index = 1; index <= botCount; index++) {
            final String name = "Bot %02d".formatted(index);
            final CatalogBot bot = new CatalogBot(name, "1.0", "Java", "bots/java/" + name,
                    "sha256:" + "%064x".formatted(index));
            bots.put(bot.displayName(), bot);
        }
        final List<CatalogBot> individuals = bots.values().stream().toList();
        for (int index = 0; index + 1 < individuals.size(); index += 2) {
            final String name = "Team %02d".formatted(index / 2 + 1);
            final CatalogBot team = new CatalogBot(name, "1.0", "JVM", "bots/java/" + name,
                    "sha256:" + "%064x".formatted(botCount + index + 1),
                    List.of(individuals.get(index).displayName(), individuals.get(index + 1).displayName()));
            bots.put(team.displayName(), team);
        }
        final Map<GameType, GameTypeSettings> settings = Map.of(
                GameType.ONE_VS_ONE, new GameTypeSettings(35, 800, 600, 2),
                GameType.TWIN_DUEL, new GameTypeSettings(75, 800, 800, 4),
                GameType.MELEE, new GameTypeSettings(35, 1000, 1000, 10));
        final List<PriorityPair> individualPairs = withAdvice ? List.of(
                new PriorityPair(List.of(bots.get("Bot 01 1.0"), bots.get("Bot 02 1.0")), 0, "new-bot"),
                new PriorityPair(List.of(bots.get("Bot 03 1.0"), bots.get("Bot 04 1.0")), 5, "under-sampled"))
                : List.of();
        final List<PriorityPair> teamPairs = withAdvice ? List.of(
                new PriorityPair(List.of(bots.get("Team 01 1.0"), bots.get("Team 04 1.0")), 0, "new-bot"),
                new PriorityPair(List.of(bots.get("Team 02 1.0"), bots.get("Team 03 1.0")), 5,
                        "under-sampled"))
                : List.of();
        final Map<GameType, MatchAdvice> advice = new LinkedHashMap<>();
        for (final GameType gameType : GameType.values()) {
            advice.put(gameType, new MatchAdvice(gameType, "a".repeat(64), 6,
                    gameType.isTeamGame() ? teamPairs : individualPairs));
        }
        return new RumbleSnapshot(URI.create("https://github.com/example/rumble-data"), "b".repeat(40),
                new EnginePin(1, "unreleased", "example/image", java.util.Optional.empty(), settings),
                new BotCatalog(URI.create("https://github.com/example/rumble-bots"), "c".repeat(40), bots),
                new ClientRegistration("alice", "alice-desktop"), advice);
    }

    private static ClientConfiguration configuration(final Set<String> ownBots) {
        return new ClientConfiguration(URI.create("https://github.com/example/rumble-bots"),
                URI.create("https://github.com/example/rumble-data"), Optional.of("alice-desktop"), ownBots,
                Set.of(GameType.values()), 10, ClientMode.RANKED, Path.of("work"));
    }
}
