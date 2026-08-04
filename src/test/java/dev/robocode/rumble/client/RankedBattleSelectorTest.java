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
    @Tag("RCL-003")
    void testRCL003_UnitPositive_prefersOwnBotAdviceAndSelectsEachPinnedParticipantCount() {
        final RumbleSnapshot snapshot = snapshot(12, true);
        final ClientConfiguration configuration = configuration(Set.of("Bot 03"));
        final RankedBattleSelector selector = new RankedBattleSelector();

        for (final GameType gameType : GameType.values()) {
            final BattleSelection selection = selector.select(snapshot, configuration, gameType, RANDOM_SEED);

            assertEquals(snapshot.engine().gameTypes().get(gameType).participants(), selection.participants().size());
            assertEquals(selection.participants().size(), Set.copyOf(selection.participants()).size());
            assertTrue(selection.participants().stream().map(CatalogBot::name)
                    .toList().containsAll(List.of("Bot 03", "Bot 04")));
            assertEquals(RANDOM_SEED, selection.randomSeed());
        }
    }

    @Test
    @Tag("RCL-003")
    void testRCL003_UnitPositive_selectsGlobalAdviceOnlyFromTheHighPriorityWindow() {
        final RumbleSnapshot baseSnapshot = snapshot(12, false);
        final List<CatalogBot> bots = baseSnapshot.catalog().activeBots().values().stream()
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
    @Tag("RCL-003")
    void testRCL003_UnitPositive_usesSeededCatalogFallbackWhenAdviceIsEmpty() {
        final RumbleSnapshot snapshot = snapshot(12, false);
        final RankedBattleSelector selector = new RankedBattleSelector();

        final BattleSelection first = selector.select(snapshot, configuration(Set.of()), GameType.MELEE, RANDOM_SEED);
        final BattleSelection repeated = selector.select(snapshot, configuration(Set.of()), GameType.MELEE, RANDOM_SEED);

        assertEquals(first, repeated);
        assertEquals(10, first.participants().size());
    }

    @Test
    @Tag("RCL-003")
    void testRCL003_UnitNegative_rejectsASelectionWithoutEnoughDistinctActiveBots() {
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
        final Map<GameType, GameTypeSettings> settings = Map.of(
                GameType.ONE_VS_ONE, new GameTypeSettings(35, 800, 600, 2),
                GameType.TWIN_DUEL, new GameTypeSettings(75, 800, 800, 4),
                GameType.MELEE, new GameTypeSettings(35, 1000, 1000, 10));
        final List<PriorityPair> pairs = withAdvice ? List.of(
                new PriorityPair(List.of(bots.get("Bot 01 1.0"), bots.get("Bot 02 1.0")), 0, "new-bot"),
                new PriorityPair(List.of(bots.get("Bot 03 1.0"), bots.get("Bot 04 1.0")), 5, "under-sampled"))
                : List.of();
        final Map<GameType, MatchAdvice> advice = new LinkedHashMap<>();
        for (final GameType gameType : GameType.values()) {
            advice.put(gameType, new MatchAdvice(gameType, "a".repeat(64), 6, pairs));
        }
        return new RumbleSnapshot(URI.create("https://github.com/example/rumble-data"), "b".repeat(40),
                new EnginePin(1, "unreleased", "example/image", settings),
                new BotCatalog(URI.create("https://github.com/example/rumble-bots"), "c".repeat(40), bots),
                new ClientRegistration("alice", "alice-desktop"), advice);
    }

    private static ClientConfiguration configuration(final Set<String> ownBots) {
        return new ClientConfiguration(URI.create("https://github.com/example/rumble-bots"),
                URI.create("https://github.com/example/rumble-data"), Optional.of("alice-desktop"), ownBots,
                Set.of(GameType.values()), 10, ClientMode.RANKED, Path.of("work"));
    }
}
