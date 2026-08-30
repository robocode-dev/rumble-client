package dev.robocode.rumble.client;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankedJournalTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @Tag("RCL-006")
    void testRCL006_IntegrationPositive_reopensFsyncedRecordAndKeepsUnacknowledgedResults() throws IOException {
        final RankedJournal journal = new RankedJournal(temporaryDirectory);
        final RankedBattleRecord first = record("9f4ee719-d4a3-4387-8964-f52877c414b0", 7);
        final RankedBattleRecord second = record("89b530c6-d06e-4730-9eb1-565efb99d877", 7);

        journal.append(first);
        journal.append(second);
        new RankedJournal(temporaryDirectory).acknowledge(List.of(new SubmissionReceipt(first.battleId(), "issue-42")));

        assertEquals(List.of(second), new RankedJournal(temporaryDirectory).pending());
        assertTrue(Files.size(temporaryDirectory.resolve("journal/records.jsonl")) > 0);
    }

    @Test
    @Tag("RCL-006")
    void testRCL006_IntegrationPositive_quarantinesObsoleteBehaviorVersionWithoutDeletingEvidence() throws IOException {
        final RankedJournal journal = new RankedJournal(temporaryDirectory);
        final RankedBattleRecord obsolete = record("44177440-36c4-4eb7-8949-5984648a1665", 6);
        final RankedBattleRecord current = record("39a63005-4d76-4d23-9d37-d378ab62f5c0", 7);
        journal.append(obsolete);
        journal.append(current);

        assertEquals(List.of(obsolete), journal.quarantineObsolete(7));
        assertEquals(List.of(current), journal.pending());
        assertTrue(Files.readString(temporaryDirectory.resolve("journal/quarantine.jsonl"))
                .contains("behavior version 6 is incompatible with 7"));
    }

    private static RankedBattleRecord record(final String battleId, final int behaviorVersion) {
        return new RankedBattleRecord(UUID.fromString(battleId), Instant.parse("2026-08-30T12:00:00Z"),
                new ClientIdentity("alice-client", "0.1.0"), new EngineIdentity(behaviorVersion), "1v1", 35,
                800, 600, 7L, List.of(new RankedParticipant("Alpha", "1.0", false, 1, 35, 35,
                0, 0, 0, 0, 0, 35, 0, 0)), "sha256:" + "a".repeat(64));
    }
}
