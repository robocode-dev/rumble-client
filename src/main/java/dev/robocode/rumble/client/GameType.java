package dev.robocode.rumble.client;

import java.util.Arrays;

/**
 * Ranked game types published by the Rumble engine pin.
 */
enum GameType {
    ONE_VS_ONE("1v1"),
    TWIN_DUEL("twinduel"),
    MELEE("melee");

    private final String contractName;

    GameType(final String contractName) {
        this.contractName = contractName;
    }

    String contractName() {
        return contractName;
    }

    static GameType fromContractName(final String value) {
        return Arrays.stream(values())
                .filter(gameType -> gameType.contractName.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported game type: " + value));
    }
}
