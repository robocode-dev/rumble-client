package dev.robocode.rumble.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Sends bounded result envelopes through the result-data Issues-only inbox and retains them until receipted.
 */
final class IssueOpsSubmission {
    private static final int MAX_RESULTS_PER_ISSUE = 60;

    private final IssueOpsTransport transport;
    private final Clock clock;

    IssueOpsSubmission(final IssueOpsTransport transport, final Clock clock) {
        this.transport = transport;
        this.clock = clock;
    }

    SubmissionReport submit(final RankedJournal journal, final RumbleSnapshot snapshot) throws IOException {
        final List<SubmissionStatus> statuses = collectStatuses(journal.unacknowledgedSubmissions(), snapshot);
        final List<SubmissionReceipt> receipts = statuses.stream().flatMap(status -> status.receipts().stream()).toList();
        journal.acknowledge(receipts);
        final Set<UUID> terminal = statuses.stream().filter(SubmissionStatus::terminal)
                .flatMap(status -> status.batch().battleIds().stream()).collect(Collectors.toSet());
        final Set<UUID> inFlight = journal.unacknowledgedSubmissions().stream().flatMap(batch -> batch.battleIds().stream())
                .filter(battleId -> !terminal.contains(battleId))
                .collect(Collectors.toSet());
        final List<RankedBattleRecord> pending = journal.pending().stream().filter(record -> !inFlight.contains(record.battleId()))
                .toList();
        final List<SubmittedBatch> submitted = new ArrayList<>();
        for (final List<RankedBattleRecord> batch : batches(pending)) {
            final SubmittedBatch issue = transport.createIssue(snapshot.canonicalDataRepository(), envelope(batch),
                    title(batch.get(0)));
            journal.recordSubmission(issue);
            submitted.add(issue);
        }
        return new SubmissionReport(receipts, submitted);
    }

    private List<SubmissionStatus> collectStatuses(final List<SubmittedBatch> submitted,
                                                    final RumbleSnapshot snapshot) throws IOException {
        final List<SubmissionStatus> statuses = new ArrayList<>();
        for (final SubmittedBatch batch : submitted) {
            final Set<UUID> expected = Set.copyOf(batch.battleIds());
            final SubmissionStatus status = transport.status(snapshot.canonicalDataRepository(), batch);
            statuses.add(new SubmissionStatus(batch, status.receipts().stream()
                    .filter(receipt -> expected.contains(receipt.battleId())).toList(), status.terminal()));
        }
        return statuses;
    }

    private static List<List<RankedBattleRecord>> batches(final List<RankedBattleRecord> records) {
        final Map<ClientIdentity, List<RankedBattleRecord>> grouped = records.stream()
                .collect(Collectors.groupingBy(RankedBattleRecord::client, java.util.LinkedHashMap::new,
                        Collectors.toList()));
        final List<List<RankedBattleRecord>> batches = new ArrayList<>();
        for (final List<RankedBattleRecord> group : grouped.values()) {
            for (int start = 0; start < group.size(); start += MAX_RESULTS_PER_ISSUE) {
                batches.add(List.copyOf(group.subList(start, Math.min(group.size(), start + MAX_RESULTS_PER_ISSUE))));
            }
        }
        return batches;
    }

    private String title(final RankedBattleRecord record) {
        return "[result] " + record.client().id() + " " + DateTimeFormatter.ISO_INSTANT.format(clock.instant());
    }

    private static String envelope(final Collection<RankedBattleRecord> records) {
        final RankedBattleRecord first = records.stream().findFirst().orElseThrow();
        if (records.stream().anyMatch(record -> !record.client().equals(first.client()))) {
            throw new IllegalArgumentException("A submission envelope may contain only one client identity");
        }
        final JsonObject envelope = new JsonObject();
        envelope.addProperty("schemaVersion", 1);
        envelope.addProperty("clientId", first.client().id());
        envelope.addProperty("clientVersion", first.client().version());
        final JsonArray results = new JsonArray();
        for (final RankedBattleRecord record : records) {
            final JsonObject result = RankedJournal.recordJson(record);
            result.remove("schemaVersion");
            results.add(result);
        }
        envelope.add("results", results);
        return "```json%n%s%n```".formatted(envelope);
    }
}

/** Reports both newly observed receipts and newly posted issue batches. */
record SubmissionReport(List<SubmissionReceipt> receipts, List<SubmittedBatch> submitted) {
    SubmissionReport {
        receipts = List.copyOf(receipts);
        submitted = List.copyOf(submitted);
    }
}

/** Issues-only boundary used by the client to post result envelopes and observe receipt comments. */
interface IssueOpsTransport {
    SubmittedBatch createIssue(URI repository, String body, String title) throws IOException;

    SubmissionStatus status(URI repository, SubmittedBatch batch) throws IOException;
}

/** One observed issue state, including its durable accepted-record receipts. */
record SubmissionStatus(SubmittedBatch batch, List<SubmissionReceipt> receipts, boolean terminal) {
    SubmissionStatus {
        receipts = List.copyOf(receipts);
    }
}
