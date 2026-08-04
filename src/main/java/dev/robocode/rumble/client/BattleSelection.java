package dev.robocode.rumble.client;

import java.util.List;

/**
 * Immutable ranked battle selection reproducible from its recorded random seed.
 */
record BattleSelection(GameType gameType, long randomSeed, List<CatalogBot> participants) {
    BattleSelection {
        participants = List.copyOf(participants);
    }
}
