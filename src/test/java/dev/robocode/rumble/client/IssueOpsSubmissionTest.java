package dev.robocode.rumble.client;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IssueOpsSubmissionTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @Tag("RCL-007")
    void testRCL007_IntegrationPositive_postsBoundedIssueEnvelopeAndAcknowledgesOnlyReceipt() throws IOException {
        final RankedBattleRecord first = record("d5ef6066-6a22-467f-88fe-08ff73540e18");
        final RankedBattleRecord second = record("fc86dfad-f03f-4ca8-b6f2-b8eb2e327a2c");
        final RankedJournal journal = new RankedJournal(temporaryDirectory);
        journal.append(first);
        journal.append(second);
        final RecordingTransport transport = new RecordingTransport(first.battleId());
        final IssueOpsSubmission submission = new IssueOpsSubmission(transport,
                Clock.fixed(Instant.parse("2026-08-30T12:00:00Z"), ZoneOffset.UTC));

        final SubmissionReport posted = submission.submit(journal, snapshot());
        final SubmissionReport receipted = submission.submit(journal, snapshot());

        assertEquals(1, posted.submitted().size());
        assertEquals(1, transport.bodies.size());
        assertTrue(transport.bodies.get(0).startsWith("```json"));
        assertTrue(transport.bodies.get(0).contains("\"clientId\":\"alice-client\""));
        assertEquals(List.of(first.battleId()), receipted.receipts().stream().map(SubmissionReceipt::battleId).toList());
        assertEquals(List.of(second), journal.pending());
    }

    @Test
    @Tag("RCL-006")
    void testRCL006_IntegrationNegative_submissionFailureLeavesEveryRecordRetryable() throws IOException {
        final RankedBattleRecord record = record("67158518-7dd0-4d0c-af40-e1981f8a348f");
        final RankedJournal journal = new RankedJournal(temporaryDirectory);
        journal.append(record);
        final IssueOpsTransport unavailable = new IssueOpsTransport() {
            @Override
            public SubmittedBatch createIssue(final URI repository, final String body, final String title) throws IOException {
                throw new IOException("offline");
            }

            @Override
            public SubmissionStatus status(final URI repository, final SubmittedBatch batch) {
                return new SubmissionStatus(batch, List.of(), false);
            }
        };

        assertThrows(IOException.class, () -> new IssueOpsSubmission(unavailable, Clock.systemUTC()).submit(journal, snapshot()));
        assertEquals(List.of(record), journal.pending());
    }

    @Test
    @Tag("RCL-006")
    void testRCL006_IntegrationNegative_terminallyRejectedBatchIsRetried() throws IOException {
        final RankedBattleRecord record = record("676b8646-5993-4b60-9a1e-6112530d1e8d");
        final RankedJournal journal = new RankedJournal(temporaryDirectory);
        journal.append(record);
        journal.recordSubmission(new SubmittedBatch(42, "https://github.com/example/rumble-data/issues/42",
                List.of(record.battleId())));
        final TerminalRejectionTransport transport = new TerminalRejectionTransport(record.battleId());

        final SubmissionReport report = new IssueOpsSubmission(transport, Clock.systemUTC()).submit(journal, snapshot());

        assertEquals(1, report.submitted().size());
        assertEquals(1, transport.bodies.size());
        assertTrue(transport.bodies.get(0).contains(record.battleId().toString()));
        assertEquals(List.of(record), journal.pending());
    }

    @Test
    @Tag("RCL-007")
    void testRCL007_IntegrationPositive_splitsSixtyOneRecordsAcrossBoundedIssueBatches() throws IOException {
        final RankedJournal journal = new RankedJournal(temporaryDirectory);
        for (int index = 0; index < 61; index++) {
            journal.append(record(UUID.randomUUID().toString()));
        }
        final BatchRecordingTransport transport = new BatchRecordingTransport();

        final SubmissionReport report = new IssueOpsSubmission(transport, Clock.systemUTC()).submit(journal, snapshot());

        assertEquals(2, report.submitted().size());
        assertEquals(List.of(60, 1), transport.bodies.stream()
                .map(body -> body.split("\"battleId\"", -1).length - 1).toList());
    }

    @Test
    @Tag("RCL-007")
    void testRCL007_IntegrationNegative_rejectsMissingIssuesOnlyCredentialBeforeAnyRequest() {
        assertThrows(IllegalArgumentException.class, () -> new GitHubIssueOpsTransport(""));
    }

    private static RumbleSnapshot snapshot() {
        return new RumbleSnapshot(URI.create("https://github.com/example/rumble-data"), "a".repeat(40),
                new EnginePin(7, "1.2.0", "image", Optional.empty(), Map.of()),
                new BotCatalog(URI.create("https://github.com/example/rumble-bots"), "b".repeat(40), Map.of()),
                new ClientRegistration("alice", "alice-client"), Map.of());
    }

    private static RankedBattleRecord record(final String battleId) {
        return new RankedBattleRecord(UUID.fromString(battleId), Instant.parse("2026-08-30T12:00:00Z"),
                new ClientIdentity("alice-client", "0.1.0"), new EngineIdentity(7), "1v1", 35, 800, 600,
                7L, List.of(new RankedParticipant("Alpha", "1.0", false, 1, 35, 35, 0, 0, 0, 0, 0, 35, 0, 0)),
                "sha256:" + "a".repeat(64));
    }

    private static final class RecordingTransport implements IssueOpsTransport {
        private final UUID accepted;
        private final List<String> bodies = new ArrayList<>();

        private RecordingTransport(final UUID accepted) {
            this.accepted = accepted;
        }

        @Override
        public SubmittedBatch createIssue(final URI repository, final String body, final String title) {
            bodies.add(body);
            return new SubmittedBatch(42, "https://github.com/example/rumble-data/issues/42", List.of(accepted,
                    UUID.fromString("fc86dfad-f03f-4ca8-b6f2-b8eb2e327a2c")));
        }

        @Override
        public SubmissionStatus status(final URI repository, final SubmittedBatch batch) {
            return new SubmissionStatus(batch, List.of(new SubmissionReceipt(accepted, batch.issueUrl())), false);
        }
    }

    private static final class BatchRecordingTransport implements IssueOpsTransport {
        private final List<String> bodies = new ArrayList<>();

        @Override
        public SubmittedBatch createIssue(final URI repository, final String body, final String title) {
            bodies.add(body);
            return new SubmittedBatch(bodies.size(), "https://github.com/example/rumble-data/issues/" + bodies.size(),
                    List.of(UUID.randomUUID()));
        }

        @Override
        public SubmissionStatus status(final URI repository, final SubmittedBatch batch) {
            return new SubmissionStatus(batch, List.of(), false);
        }
    }

    private static final class TerminalRejectionTransport implements IssueOpsTransport {
        private final UUID battleId;
        private final List<String> bodies = new ArrayList<>();

        private TerminalRejectionTransport(final UUID battleId) {
            this.battleId = battleId;
        }

        @Override
        public SubmittedBatch createIssue(final URI repository, final String body, final String title) {
            bodies.add(body);
            return new SubmittedBatch(43, "https://github.com/example/rumble-data/issues/43", List.of(battleId));
        }

        @Override
        public SubmissionStatus status(final URI repository, final SubmittedBatch batch) {
            return new SubmissionStatus(batch, List.of(), batch.issueNumber() == 42);
        }
    }
}
