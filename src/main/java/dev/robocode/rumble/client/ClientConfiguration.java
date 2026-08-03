package dev.robocode.rumble.client;

import java.net.URI;
import java.nio.file.Path;
import java.util.Set;

/**
 * Validated local settings that determine how the client may run.
 *
 * @param botsRepository reviewed bot catalog repository.
 * @param dataRepository Rumble data repository or its canonical predecessor.
 * @param clientId registered client identity.
 * @param myBots local own-bot scheduling hints.
 * @param gameTypes selected ranked game types.
 * @param battlesPerSession maximum battles requested for one session.
 * @param mode local execution mode.
 * @param workDirectory local cache, journal, and evidence root.
 */
record ClientConfiguration(URI botsRepository, URI dataRepository, String clientId, Set<String> myBots,
                           Set<GameType> gameTypes, int battlesPerSession, ClientMode mode, Path workDirectory) {
    ClientConfiguration {
        myBots = Set.copyOf(myBots);
        gameTypes = Set.copyOf(gameTypes);
        workDirectory = workDirectory.toAbsolutePath().normalize();
    }
}
