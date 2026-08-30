package dev.robocode.rumble.client;

import java.util.Arrays;

/**
 * Ranked game types published by the Rumble engine pin.
 */
enum GameType {
    ONE_VS_ONE("1v1", 1),
    TWIN_DUEL("twinduel", 2),
    MELEE("melee", 1);

    private final String contractName;
    private final int teamSize;

    GameType(final String contractName, final int teamSize) {
        this.contractName = contractName;
        this.teamSize = teamSize;
    }

    String contractName() {
        return contractName;
    }

    /**
     * Number of bots each catalog entry of this game type expands to when the battle is booted.
     */
    int teamSize() {
        return teamSize;
    }

    boolean isTeamGame() {
        return teamSize > 1;
    }

    static GameType fromContractName(final String value) {
        return Arrays.stream(values())
                .filter(gameType -> gameType.contractName.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported game type: " + value));
    }
}
