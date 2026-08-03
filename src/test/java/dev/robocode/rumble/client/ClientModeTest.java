package dev.robocode.rumble.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class ClientModeTest {
    @Test
    @Tag("Unit")
    void testUnitPositive_rankedModePermitsJournal() {
        assertTrue(ClientMode.RANKED.permitsRankedJournal());
    }

    @Test
    @Tag("Unit")
    void testUnitNegative_practiceModeRejectsJournal() {
        assertFalse(ClientMode.PRACTICE.permitsRankedJournal());
    }
}
