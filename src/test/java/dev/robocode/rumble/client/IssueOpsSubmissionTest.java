package dev.robocode.rumble.client;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IssueOpsSubmissionTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-30T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path temporaryDirectory;

    @Test
    @Tag("RCL-013")
    void testRCL013_IntegrationPositive_writesPlainTextBatchAndFormAddressWithoutAnyCredential() throws IOException {
        final RankedBattleRecord first = record("d5ef6066-6a22-467f-88fe-08ff73540e18");
        final RankedBattleRecord second = record("fc86dfad-f03f-4ca8-b6f2-b8eb2e327a2c");
        final RankedJournal journal = new RankedJournal(temporaryDirectory);
        journal.append(first);
        journal.append(second);

        final SubmissionReport report = submission(new ContributorIssues()).submit(journal, snapshot());

        assertEquals(1, report.waiting().size());
        final PreparedPost post = report.waiting().get(0);
        final String content = Files.readString(post.file());
        assertTrue(post.file().getFileName().toString().endsWith(".txt"));
        assertTrue(content.startsWith("```json"));
        assertEquals(1, content.split("```json", -1).length - 1);
        assertTrue(content.contains("\"clientId\":\"alice-client\""));
        assertEquals(post.batch().body(), content);
        assertTrue(post.formAddress().startsWith(
                "https://github.com/example/rumble-data/issues/new?template=result-submission.yml&title="));
        assertTrue(post.formAddress().contains("alice-client"));
        assertEquals(List.of(first, second), journal.pending());
    }

    @Test
    @Tag("RCL-013")
    void testRCL013_IntegrationPositive_findsThePostedIssueAndAcknowledgesOnlyReceiptedResults() throws IOException {
        final RankedBattleRecord first = record("d5ef6066-6a22-467f-88fe-08ff73540e18");
        final RankedBattleRecord second = record("fc86dfad-f03f-4ca8-b6f2-b8eb2e327a2c");
        final RankedJournal journal = new RankedJournal(temporaryDirectory);
        journal.append(first);
        journal.append(second);
        final ContributorIssues issues = new ContributorIssues();
        final IssueOpsSubmission submission = submission(issues);
        final PreparedPost post = submission.submit(journal, snapshot()).waiting().get(0);

        issues.post(post.batch().title(), 42, Set.of(first.battleId()));
        final SubmissionReport collected = submission.submit(journal, snapshot());

        assertEquals(List.of(first.battleId()), collected.receipts().stream().map(SubmissionReceipt::battleId).toList());
        assertEquals(List.of(second), journal.pending());
        assertTrue(collected.waiting().isEmpty());
    }

    @Test
    @Tag("RCL-013")
    void testRCL013_IntegrationPositive_splitsByResultCountAndByBodySize() throws IOException {
        final RankedJournal journal = new RankedJournal(temporaryDirectory);
        for (int index = 0; index < 61; index++) {
            journal.append(record(UUID.randomUUID().toString()));
        }
        final SubmissionReport counted = submission(new ContributorIssues()).submit(journal, snapshot());
        assertEquals(List.of(60, 1), counted.waiting().stream()
                .map(post -> post.batch().battleIds().size()).toList());

        final RankedJournal large = new RankedJournal(temporaryDirectory.resolve("large"));
        for (int index = 0; index < 30; index++) {
            large.append(record(UUID.randomUUID().toString(), "B".repeat(3000)));
        }
        final SubmissionReport sized = new IssueOpsSubmission(new ContributorIssues(), CLOCK,
                temporaryDirectory.resolve("large-files")).submit(large, snapshot());
        assertTrue(sized.waiting().size() > 1);
        assertTrue(sized.waiting().stream().allMatch(post -> post.batch().body().length()
                <= IssueOpsSubmission.MAX_BODY_CHARACTERS));
        assertEquals(30, sized.waiting().stream().mapToInt(post -> post.batch().battleIds().size()).sum());
        assertEquals(sized.waiting().size(), sized.waiting().stream()
                .map(post -> post.batch().title()).distinct().count());
    }

    @Test
    @Tag("RCL-013")
    void testRCL013_IntegrationNegative_constructsTheReadOnlyTransportWithoutACredential() {
        new GitHubIssueOpsTransport("");
        new GitHubIssueOpsTransport((String) null);
    }

    @Test
    @Tag("RCL-006")
    void testRCL006_IntegrationNegative_repeatedSubmissionBeforePostingDoesNotCreateDuplicateBatches() throws IOException {
        final RankedBattleRecord record = record("67158518-7dd0-4d0c-af40-e1981f8a348f");
        final RankedJournal journal = new RankedJournal(temporaryDirectory);
        journal.append(record);
        final IssueOpsSubmission submission = submission(new ContributorIssues());

        final PreparedPost first = submission.submit(journal, snapshot()).waiting().get(0);
        final SubmissionReport again = submission.submit(journal, snapshot());

        assertEquals(1, again.waiting().size());
        assertEquals(first.batch().title(), again.waiting().get(0).batch().title());
        assertEquals(List.of(record), journal.pending());
    }

    @Test
    @Tag("RCL-006")
    void testRCL006_IntegrationNegative_interruptedLookupLeavesEveryRecordRetryable() throws IOException {
        final RankedBattleRecord record = record("676b8646-5993-4b60-9a1e-6112530d1e8d");
        final RankedJournal journal = new RankedJournal(temporaryDirectory);
        journal.append(record);
        final ContributorIssues issues = new ContributorIssues();
        final IssueOpsSubmission prepared = submission(issues);
        final PreparedPost post = prepared.submit(journal, snapshot()).waiting().get(0);
        issues.post(post.batch().title(), 42, Set.of(record.battleId()));
        issues.offline = true;

        assertThrows(IOException.class, () -> prepared.submit(journal, snapshot()));
        assertEquals(List.of(record), journal.pending());

        issues.offline = false;
        final SubmissionReport recovered = prepared.submit(journal, snapshot());
        assertEquals(1, recovered.receipts().size());
        assertTrue(journal.pending().isEmpty());
    }

    @Test
    @Tag("RCL-006")
    void testRCL006_IntegrationNegative_terminallyRejectedBatchIsPreparedAgain() throws IOException {
        final RankedBattleRecord record = record("676b8646-5993-4b60-9a1e-6112530d1e8d");
        final RankedJournal journal = new RankedJournal(temporaryDirectory);
        journal.append(record);
        journal.recordSubmission(new SubmittedBatch(42, "https://github.com/example/rumble-data/issues/42",
                List.of(record.battleId())));
        final ContributorIssues issues = new ContributorIssues();
        issues.closedWithoutReceipts.add(42);

        final SubmissionReport report = submission(issues).submit(journal, snapshot());

        assertEquals(1, report.waiting().size());
        assertTrue(report.waiting().get(0).batch().body().contains(record.battleId().toString()));
        assertEquals(List.of(record), journal.pending());
    }

    @Test
    @Tag("RCL-006")
    void testRCL006_IntegrationNegative_unknownFormTitleDoesNotAcknowledgeAnything() throws IOException {
        final RankedBattleRecord record = record("0b1a83ee-8c63-4f43-8a5c-0d3a5a1e9f11");
        final RankedJournal journal = new RankedJournal(temporaryDirectory);
        journal.append(record);
        final ContributorIssues issues = new ContributorIssues();
        issues.post("[result] someone-else 2026-08-30T12:00:00Z", 7, Set.of(record.battleId()));

        final SubmissionReport report = submission(issues).submit(journal, snapshot());

        assertTrue(report.receipts().isEmpty());
        assertFalse(report.waiting().isEmpty());
        assertEquals(List.of(record), journal.pending());
    }

    private IssueOpsSubmission submission(final IssueOpsTransport transport) {
        return new IssueOpsSubmission(transport, CLOCK, temporaryDirectory.resolve("submissions"));
    }

    private static RumbleSnapshot snapshot() {
        return new RumbleSnapshot(URI.create("https://github.com/example/rumble-data"), "a".repeat(40),
                new EnginePin(7, "1.2.0", "image", Optional.empty(), Map.of()),
                new BotCatalog(URI.create("https://github.com/example/rumble-bots"), "b".repeat(40), Map.of()),
                new ClientRegistration("alice", "alice-client"), Map.of());
    }

    private static RankedBattleRecord record(final String battleId) {
        return record(battleId, "Alpha");
    }

    private static RankedBattleRecord record(final String battleId, final String botName) {
        return new RankedBattleRecord(UUID.fromString(battleId), Instant.parse("2026-08-30T12:00:00Z"),
                new ClientIdentity("alice-client", "0.1.0"), new EngineIdentity(7), "1v1", 35, 800, 600,
                7L, List.of(new RankedParticipant(botName, "1.0", false, 1, 35, 35, 0, 0, 0, 0, 0, 35, 0, 0)),
                "sha256:" + "a".repeat(64));
    }

    /** Models the public issue list: only issues a contributor has actually posted through the form exist. */
    private static final class ContributorIssues implements IssueOpsTransport {
        private final Map<String, Integer> numbersByTitle = new HashMap<>();
        private final Map<Integer, Set<UUID>> receiptedByNumber = new HashMap<>();
        private final Set<Integer> closedWithoutReceipts = new java.util.HashSet<>();
        private boolean offline;

        void post(final String title, final int number, final Set<UUID> receipted) {
            numbersByTitle.put(title, number);
            receiptedByNumber.put(number, receipted);
        }

        @Override
        public Optional<SubmittedBatch> findIssue(final URI repository, final String title,
                                                  final List<UUID> battleIds) throws IOException {
            if (offline) {
                throw new IOException("offline");
            }
            final Integer number = numbersByTitle.get(title);
            return number == null ? Optional.empty() : Optional.of(new SubmittedBatch(number,
                    "https://github.com/example/rumble-data/issues/" + number, battleIds));
        }

        @Override
        public SubmissionStatus status(final URI repository, final SubmittedBatch batch) throws IOException {
            if (offline) {
                throw new IOException("offline");
            }
            final List<SubmissionReceipt> receipts = new ArrayList<>();
            for (final UUID battleId : receiptedByNumber.getOrDefault(batch.issueNumber(), Set.of())) {
                receipts.add(new SubmissionReceipt(battleId, batch.issueUrl()));
            }
            return new SubmissionStatus(batch, receipts, closedWithoutReceipts.contains(batch.issueNumber()));
        }
    }
}
