package dev.robocode.rumble.client;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Immutable result record ready for the Rumble result-data envelope. */
record RankedBattleRecord(UUID battleId, Instant completedAt, ClientIdentity client, EngineIdentity engine,
                          String gameType, int rounds, int arenaWidth, int arenaHeight,
                          long selectionSeed, List<RankedParticipant> participants, String replayHash) {
    RankedBattleRecord {
        participants = List.copyOf(participants);
    }
}

record ClientIdentity(String id, String version) {
}

record EngineIdentity(int behaviorVersion) {
}

record RankedParticipant(String name, String version, boolean isTeam, int rank, int totalScore,
                         int survival, int lastSurvivorBonus, int bulletDamage, int bulletKillBonus,
                         int ramDamage, int ramKillBonus, int firstPlaces, int secondPlaces,
                         int thirdPlaces) {
}
