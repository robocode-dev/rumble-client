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

/**
 * An active catalog entry.
 *
 * @param gameTypes the game types the entry declares; empty when it declares none and so plays every game type of
 *                  its kind.
 */
record CatalogBot(String name, String version, String platform, String path, String sourceHash,
                  List<String> teamMembers, List<String> gameTypes) {
    CatalogBot {
        teamMembers = List.copyOf(teamMembers);
        gameTypes = List.copyOf(gameTypes);
    }

    CatalogBot(final String name, final String version, final String platform, final String path,
               final String sourceHash, final List<String> teamMembers) {
        this(name, version, platform, path, sourceHash, teamMembers, List.of());
    }

    CatalogBot(final String name, final String version, final String platform, final String path,
               final String sourceHash) {
        this(name, version, platform, path, sourceHash, List.of());
    }

    /** Returns whether this entry may take part in a ranked battle of the game type. */
    boolean playsGameType(final GameType gameType) {
        return isTeam() == gameType.isTeamGame()
                && (gameTypes.isEmpty() || gameTypes.contains(gameType.contractName()));
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
