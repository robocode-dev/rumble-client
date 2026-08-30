package dev.robocode.rumble.client;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable ranked input snapshot accepted from one Rumble data revision.
 */
record RumbleSnapshot(URI canonicalDataRepository, String dataRevision, EnginePin engine, BotCatalog catalog,
                      ClientRegistration registration, Map<GameType, MatchAdvice> advice) {
    RumbleSnapshot {
        advice = Map.copyOf(advice);
    }
}

record EnginePin(int behaviorVersion, String tankRoyaleVersion, String image, Optional<String> clientImage,
                 Map<GameType, GameTypeSettings> gameTypes) {
    EnginePin {
        clientImage = java.util.Objects.requireNonNull(clientImage, "clientImage");
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

record CatalogBot(String name, String version, String platform, String path, String sourceHash,
                  List<String> teamMembers) {
    CatalogBot {
        teamMembers = List.copyOf(teamMembers);
    }

    CatalogBot(final String name, final String version, final String platform, final String path,
               final String sourceHash) {
        this(name, version, platform, path, sourceHash, List.of());
    }

    String displayName() {
        return name + " " + version;
    }

    boolean isTeam() {
        return !teamMembers.isEmpty();
    }

    int expandedParticipantCount() {
        return isTeam() ? teamMembers.size() : 1;
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
