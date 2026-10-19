package dev.robocode.rumble.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Prepares bounded result envelopes for the result form of the data repository and retains them until receipted.
 * The contributor posts each prepared batch through the form, so no submission credential is needed.
 */
final class IssueOpsSubmission {
    private static final int MAX_RESULTS_PER_ISSUE = 60;
    /** Serialized size kept well below the issue body limit of 65,536 characters. */
    static final int MAX_BODY_CHARACTERS = 60_000;

    private final IssueOpsTransport transport;
    private final Clock clock;
    private final Path submissionDirectory;
    private final PublishedResults publishedResults;

    IssueOpsSubmission(final IssueOpsTransport transport, final Clock clock, final Path submissionDirectory) {
        this(transport, clock, submissionDirectory, new CanonicalPublishedResults(new GitRepositoryReader()));
    }

    IssueOpsSubmission(final IssueOpsTransport transport, final Clock clock, final Path submissionDirectory,
                       final PublishedResults publishedResults) {
        this.transport = transport;
        this.clock = clock;
        this.submissionDirectory = submissionDirectory;
        this.publishedResults = publishedResults;
    }

    SubmissionReport submit(final RankedJournal journal, final RumbleSnapshot snapshot) throws IOException {
        for (final PreparedBatch prepared : journal.unpostedPrepared()) {
            final Optional<SubmittedBatch> posted = transport.findIssue(snapshot.canonicalDataRepository(),
                    prepared, snapshot.registration().account());
            if (posted.isPresent()) {
                journal.recordSubmission(posted.get(), prepared.title());
            }
        }
        final List<SubmissionStatus> statuses = collectStatuses(journal.unacknowledgedSubmissions(), snapshot);
        final Set<UUID> claimed = statuses.stream().flatMap(status -> status.receipts().stream())
                .map(SubmissionReceipt::battleId).collect(Collectors.toSet());
        final Set<UUID> published = claimed.isEmpty() ? Set.of() : publishedResults.find(
                snapshot.canonicalDataRepository(), journal.pending().stream()
                        .filter(record -> claimed.contains(record.battleId())).toList(), snapshot.registration().account());
        final List<SubmissionReceipt> receipts = statuses.stream().flatMap(status -> status.receipts().stream())
                .filter(receipt -> published.contains(receipt.battleId())).toList();
        journal.acknowledge(receipts);
        final Set<UUID> inFlight = statuses.stream().filter(status -> !status.terminal()
                        || status.receipts().stream().anyMatch(receipt -> !published.contains(receipt.battleId())))
                .flatMap(status -> status.batch().battleIds().stream())
                .collect(Collectors.toSet());
        journal.unpostedPrepared().forEach(batch -> inFlight.addAll(batch.battleIds()));
        final List<RankedBattleRecord> pending = journal.pending().stream().filter(record -> !inFlight.contains(record.battleId()))
                .toList();
        Instant stamp = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        final Set<String> titles = journal.preparedTitles();
        for (final List<RankedBattleRecord> batch : batches(pending)) {
            while (titles.contains(title(batch.get(0), stamp))) {
                stamp = stamp.plusMillis(1);
            }
            journal.recordPrepared(new PreparedBatch(title(batch.get(0), stamp), envelope(batch),
                    batch.stream().map(RankedBattleRecord::battleId).toList()));
            stamp = stamp.plusMillis(1);
        }
        final List<PreparedPost> waiting = new ArrayList<>();
        for (final PreparedBatch prepared : journal.unpostedPrepared()) {
            waiting.add(write(prepared, snapshot.canonicalDataRepository()));
        }
        return new SubmissionReport(receipts, waiting);
    }

    private PreparedPost write(final PreparedBatch prepared, final URI repository) throws IOException {
        Files.createDirectories(submissionDirectory);
        final Path file = submissionDirectory.resolve("submission-"
                + prepared.title().replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "") + ".txt");
        Files.writeString(file, prepared.body(), StandardCharsets.UTF_8);
        return new PreparedPost(prepared, file, formAddress(repository, prepared.title()));
    }

    static String formAddress(final URI repository, final String title) {
        final String path = repository.getPath().replaceFirst("/$", "").replaceFirst("\\.git$", "");
        return "https://" + repository.getHost() + path + "/issues/new?template=result-submission.yml&title="
                + URLEncoder.encode(title, StandardCharsets.UTF_8);
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

    private static List<List<RankedBattleRecord>> batches(final List<RankedBattleRecord> records) throws IOException {
        final Map<ClientIdentity, List<RankedBattleRecord>> grouped = records.stream()
                .collect(Collectors.groupingBy(RankedBattleRecord::client, java.util.LinkedHashMap::new,
                        Collectors.toList()));
        final List<List<RankedBattleRecord>> batches = new ArrayList<>();
        for (final List<RankedBattleRecord> group : grouped.values()) {
            List<RankedBattleRecord> current = new ArrayList<>();
            for (final RankedBattleRecord record : group) {
                if (envelope(List.of(record)).length() > MAX_BODY_CHARACTERS) {
                    throw new IOException("Result " + record.battleId() + " exceeds the submission body limit of "
                            + MAX_BODY_CHARACTERS + " characters; the record remains pending");
                }
                final List<RankedBattleRecord> candidate = new ArrayList<>(current);
                candidate.add(record);
                if (!current.isEmpty() && (candidate.size() > MAX_RESULTS_PER_ISSUE
                        || envelope(candidate).length() > MAX_BODY_CHARACTERS)) {
                    batches.add(List.copyOf(current));
                    current = new ArrayList<>();
                }
                current.add(record);
            }
            if (!current.isEmpty()) {
                batches.add(List.copyOf(current));
            }
        }
        return batches;
    }

    private static String title(final RankedBattleRecord record, final Instant stamp) {
        return "[result] " + record.client().id() + " " + DateTimeFormatter.ISO_INSTANT.format(stamp);
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

/** Reports newly observed receipts and the prepared batches still waiting to be posted through the form. */
record SubmissionReport(List<SubmissionReceipt> receipts, List<PreparedPost> waiting) {
    SubmissionReport {
        receipts = List.copyOf(receipts);
        waiting = List.copyOf(waiting);
    }
}

/** One prepared batch with the plain-text file to paste and the form address that opens with its title. */
record PreparedPost(PreparedBatch batch, Path file, String formAddress) {
}

/** Read-only boundary used by the client to find posted result issues and observe receipt comments. */
interface IssueOpsTransport {
    Optional<SubmittedBatch> findIssue(URI repository, PreparedBatch prepared, String account) throws IOException;

    SubmissionStatus status(URI repository, SubmittedBatch batch) throws IOException;
}

/** One observed issue state, including its durable accepted-record receipts. */
record SubmissionStatus(SubmittedBatch batch, List<SubmissionReceipt> receipts, boolean terminal) {
    SubmissionStatus {
        receipts = List.copyOf(receipts);
    }
}
