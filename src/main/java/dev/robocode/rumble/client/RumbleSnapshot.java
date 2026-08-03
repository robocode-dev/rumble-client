package dev.robocode.rumble.client;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Immutable ranked input snapshot accepted from one Rumble data revision.
 */
record RumbleSnapshot(URI canonicalDataRepository, String dataRevision, EnginePin engine, BotCatalog catalog,
                      ClientRegistration registration, Map<GameType, MatchAdvice> advice) {
    RumbleSnapshot {
        advice = Map.copyOf(advice);
    }
}

record EnginePin(int behaviorVersion, String tankRoyaleVersion, String image,
                 Map<GameType, GameTypeSettings> gameTypes) {
    EnginePin {
        gameTypes = Map.copyOf(gameTypes);
    }
}

record GameTypeSettings(int rounds, int arenaWidth, int arenaHeight, int participants) {
}

record BotCatalog(URI source, String sourceCommit, Map<String, CatalogBot> activeBots) {
    BotCatalog {
        activeBots = Map.copyOf(activeBots);
    }
}

record CatalogBot(String name, String version, String platform, String path, String sourceHash) {
    String displayName() {
        return name + " " + version;
    }
}

record ClientRegistration(String account, String clientId) {
}

record MatchAdvice(GameType gameType, String projectionId, int targetSamplesPerPairing,
                   List<PriorityPair> priorityPairs) {
    MatchAdvice {
        priorityPairs = List.copyOf(priorityPairs);
    }
}

record PriorityPair(List<CatalogBot> bots, int existingSamples, String reason) {
    PriorityPair {
        bots = List.copyOf(bots);
    }
}
