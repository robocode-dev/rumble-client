package dev.robocode.rumble.client;

import dev.robocode.tankroyale.runner.BattleRunner;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

final class RunnerDependencyTest {
    @Test
    @Tag("Unit")
    void testUnitPositive_localRunnerProvidesBehaviorVersionPrecondition() {
        assertDoesNotThrow(() -> {
            try (BattleRunner ignored = BattleRunner.create(builder -> builder.requireBehaviorVersion(1))) {
                // Constructing the Runner proves the Java-facing BR-049 API is present.
            }
        });
    }
}
