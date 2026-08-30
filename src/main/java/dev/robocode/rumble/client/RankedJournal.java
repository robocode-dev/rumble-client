package dev.robocode.rumble.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Durable append-only storage for completed ranked records and their subsequent disposition.
 */
final class RankedJournal {
    private static final int SCHEMA_VERSION = 1;
    private static final String RECORDS_FILE = "records.jsonl";
    private static final String ACKNOWLEDGEMENTS_FILE = "acknowledgements.jsonl";
    private static final String QUARANTINE_FILE = "quarantine.jsonl";
    private static final String SUBMISSIONS_FILE = "submissions.jsonl";

    private final Path directory;

    RankedJournal(final Path workDirectory) {
        directory = workDirectory.resolve("journal");
    }

    void append(final RankedBattleRecord record) throws IOException {
        appendLine(RECORDS_FILE, recordJson(record));
    }

    List<RankedBattleRecord> pending() throws IOException {
        final Map<UUID, RankedBattleRecord> records = records();
        acknowledged().forEach(records::remove);
        quarantined().forEach(records::remove);
        return List.copyOf(records.values());
    }

    void acknowledge(final Collection<SubmissionReceipt> receipts) throws IOException {
        for (final SubmissionReceipt receipt : receipts) {
            appendLine(ACKNOWLEDGEMENTS_FILE, dispositionJson(receipt.battleId(), "receipt", receipt.reference()));
        }
    }

    void recordSubmission(final SubmittedBatch batch) throws IOException {
        final JsonObject entry = new JsonObject();
        entry.addProperty("schemaVersion", SCHEMA_VERSION);
        entry.addProperty("issueNumber", batch.issueNumber());
        entry.addProperty("issueUrl", batch.issueUrl());
        final JsonArray battleIds = new JsonArray();
        batch.battleIds().forEach(battleId -> battleIds.add(battleId.toString()));
        entry.add("battleIds", battleIds);
        appendLine(SUBMISSIONS_FILE, entry);
    }

    List<SubmittedBatch> unacknowledgedSubmissions() throws IOException {
        final Set<UUID> pending = pending().stream().map(RankedBattleRecord::battleId)
                .collect(java.util.stream.Collectors.toSet());
        return entries(SUBMISSIONS_FILE).stream().map(RankedJournal::submittedBatch)
                .filter(batch -> batch.battleIds().stream().anyMatch(pending::contains)).toList();
    }

    List<RankedBattleRecord> quarantineObsolete(final int behaviorVersion) throws IOException {
        final List<RankedBattleRecord> obsolete = pending().stream()
                .filter(record -> record.engine().behaviorVersion() != behaviorVersion)
                .toList();
        for (final RankedBattleRecord record : obsolete) {
            appendLine(QUARANTINE_FILE, dispositionJson(record.battleId(), "reason",
                    "behavior version " + record.engine().behaviorVersion() + " is incompatible with " + behaviorVersion));
        }
        return obsolete;
    }

    private Map<UUID, RankedBattleRecord> records() throws IOException {
        final Map<UUID, RankedBattleRecord> records = new LinkedHashMap<>();
        for (final JsonObject entry : entries(RECORDS_FILE)) {
            final RankedBattleRecord record = record(entry);
            if (records.putIfAbsent(record.battleId(), record) != null) {
                throw new IllegalArgumentException("Ranked journal contains duplicate battle ID " + record.battleId());
            }
        }
        return records;
    }

    private Set<UUID> acknowledged() throws IOException {
        return dispositionIds(ACKNOWLEDGEMENTS_FILE, "receipt");
    }

    private Set<UUID> quarantined() throws IOException {
        return dispositionIds(QUARANTINE_FILE, "reason");
    }

    private Set<UUID> dispositionIds(final String file, final String requiredField) throws IOException {
        final Set<UUID> result = new LinkedHashSet<>();
        for (final JsonObject entry : entries(file)) {
            requiredString(entry, requiredField, file);
            result.add(UUID.fromString(requiredString(entry, "battleId", file)));
        }
        return result;
    }

    private List<JsonObject> entries(final String file) throws IOException {
        final Path path = directory.resolve(file);
        if (!Files.exists(path)) {
            return List.of();
        }
        try (var lines = Files.lines(path, StandardCharsets.UTF_8)) {
            return lines.filter(line -> !line.isBlank()).map(line -> parseEntry(line, file)).toList();
        }
    }

    private void appendLine(final String file, final JsonObject entry) throws IOException {
        Files.createDirectories(directory);
        final ByteBuffer bytes = StandardCharsets.UTF_8.encode(entry + System.lineSeparator());
        try (FileChannel channel = FileChannel.open(directory.resolve(file), StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            while (bytes.hasRemaining()) {
                channel.write(bytes);
            }
            channel.force(true);
        }
    }

    private static JsonObject parseEntry(final String line, final String file) {
        try {
            final JsonElement parsed = JsonParser.parseString(line);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("Ranked journal " + file + " contains a non-object entry");
            }
            final JsonObject entry = parsed.getAsJsonObject();
            if (integer(entry, "schemaVersion", file) != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Ranked journal " + file + " has an unsupported schema version");
            }
            return entry;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Ranked journal " + file + " contains invalid JSON", exception);
        }
    }

    static JsonObject recordJson(final RankedBattleRecord record) {
        final JsonObject result = new JsonObject();
        result.addProperty("schemaVersion", SCHEMA_VERSION);
        result.addProperty("battleId", record.battleId().toString());
        result.addProperty("completedAt", record.completedAt().toString());
        result.add("client", clientJson(record.client()));
        final JsonObject engine = new JsonObject();
        engine.addProperty("behaviorVersion", record.engine().behaviorVersion());
        result.add("engine", engine);
        result.addProperty("gameType", record.gameType());
        result.addProperty("rounds", record.rounds());
        result.addProperty("arenaWidth", record.arenaWidth());
        result.addProperty("arenaHeight", record.arenaHeight());
        result.addProperty("selectionSeed", record.selectionSeed());
        final JsonArray participants = new JsonArray();
        record.participants().forEach(participant -> participants.add(participantJson(participant)));
        result.add("participants", participants);
        result.addProperty("replayHash", record.replayHash());
        return result;
    }

    private static JsonObject clientJson(final ClientIdentity client) {
        final JsonObject result = new JsonObject();
        result.addProperty("id", client.id());
        result.addProperty("version", client.version());
        return result;
    }

    private static JsonObject participantJson(final RankedParticipant participant) {
        final JsonObject result = new JsonObject();
        result.addProperty("name", participant.name());
        result.addProperty("version", participant.version());
        result.addProperty("isTeam", participant.isTeam());
        result.addProperty("rank", participant.rank());
        result.addProperty("totalScore", participant.totalScore());
        result.addProperty("survival", participant.survival());
        result.addProperty("lastSurvivorBonus", participant.lastSurvivorBonus());
        result.addProperty("bulletDamage", participant.bulletDamage());
        result.addProperty("bulletKillBonus", participant.bulletKillBonus());
        result.addProperty("ramDamage", participant.ramDamage());
        result.addProperty("ramKillBonus", participant.ramKillBonus());
        result.addProperty("firstPlaces", participant.firstPlaces());
        result.addProperty("secondPlaces", participant.secondPlaces());
        result.addProperty("thirdPlaces", participant.thirdPlaces());
        return result;
    }

    private static JsonObject dispositionJson(final UUID battleId, final String field, final String value) {
        final JsonObject result = new JsonObject();
        result.addProperty("schemaVersion", SCHEMA_VERSION);
        result.addProperty("battleId", battleId.toString());
        result.addProperty(field, value);
        return result;
    }

    private static RankedBattleRecord record(final JsonObject json) {
        final JsonObject client = object(json, "client", RECORDS_FILE);
        final JsonObject engine = object(json, "engine", RECORDS_FILE);
        final List<RankedParticipant> participants = array(json, "participants", RECORDS_FILE).asList().stream()
                .map(element -> participant(element.getAsJsonObject())).toList();
        return new RankedBattleRecord(UUID.fromString(requiredString(json, "battleId", RECORDS_FILE)),
                Instant.parse(requiredString(json, "completedAt", RECORDS_FILE)),
                new ClientIdentity(requiredString(client, "id", RECORDS_FILE), requiredString(client, "version", RECORDS_FILE)),
                new EngineIdentity(integer(engine, "behaviorVersion", RECORDS_FILE)),
                requiredString(json, "gameType", RECORDS_FILE), integer(json, "rounds", RECORDS_FILE),
                integer(json, "arenaWidth", RECORDS_FILE), integer(json, "arenaHeight", RECORDS_FILE),
                longValue(json, "selectionSeed", RECORDS_FILE), participants,
                requiredString(json, "replayHash", RECORDS_FILE));
    }

    private static SubmittedBatch submittedBatch(final JsonObject json) {
        final int issueNumber = integer(json, "issueNumber", SUBMISSIONS_FILE);
        if (issueNumber < 1) {
            throw new IllegalArgumentException("Ranked journal submissions.jsonl has invalid issueNumber");
        }
        final List<UUID> battleIds = array(json, "battleIds", SUBMISSIONS_FILE).asList().stream()
                .map(JsonElement::getAsString).map(UUID::fromString).toList();
        if (battleIds.isEmpty() || new LinkedHashSet<>(battleIds).size() != battleIds.size()) {
            throw new IllegalArgumentException("Ranked journal submissions.jsonl has invalid battleIds");
        }
        return new SubmittedBatch(issueNumber, requiredString(json, "issueUrl", SUBMISSIONS_FILE), battleIds);
    }

    private static RankedParticipant participant(final JsonObject json) {
        return new RankedParticipant(requiredString(json, "name", RECORDS_FILE),
                requiredString(json, "version", RECORDS_FILE), booleanValue(json, "isTeam", RECORDS_FILE),
                integer(json, "rank", RECORDS_FILE), integer(json, "totalScore", RECORDS_FILE),
                integer(json, "survival", RECORDS_FILE), integer(json, "lastSurvivorBonus", RECORDS_FILE),
                integer(json, "bulletDamage", RECORDS_FILE), integer(json, "bulletKillBonus", RECORDS_FILE),
                integer(json, "ramDamage", RECORDS_FILE), integer(json, "ramKillBonus", RECORDS_FILE),
                integer(json, "firstPlaces", RECORDS_FILE), integer(json, "secondPlaces", RECORDS_FILE),
                integer(json, "thirdPlaces", RECORDS_FILE));
    }

    private static JsonObject object(final JsonObject json, final String field, final String file) {
        final JsonElement value = json.get(field);
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException("Ranked journal " + file + " has invalid " + field);
        }
        return value.getAsJsonObject();
    }

    private static JsonArray array(final JsonObject json, final String field, final String file) {
        final JsonElement value = json.get(field);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException("Ranked journal " + file + " has invalid " + field);
        }
        return value.getAsJsonArray();
    }

    private static String requiredString(final JsonObject json, final String field, final String file) {
        final JsonElement value = json.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()) {
            throw new IllegalArgumentException("Ranked journal " + file + " has invalid " + field);
        }
        return value.getAsString();
    }

    private static int integer(final JsonObject json, final String field, final String file) {
        try {
            return json.get(field).getAsInt();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Ranked journal " + file + " has invalid " + field, exception);
        }
    }

    private static long longValue(final JsonObject json, final String field, final String file) {
        try {
            return json.get(field).getAsLong();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Ranked journal " + file + " has invalid " + field, exception);
        }
    }

    private static boolean booleanValue(final JsonObject json, final String field, final String file) {
        final JsonElement value = json.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Ranked journal " + file + " has invalid " + field);
        }
        return value.getAsBoolean();
    }
}

/** One durable acknowledgement published by the result-data ingestion workflow. */
record SubmissionReceipt(UUID battleId, String reference) {
}

/** One locally recorded issue containing a batch awaiting result-data receipts. */
record SubmittedBatch(int issueNumber, String issueUrl, List<UUID> battleIds) {
    SubmittedBatch {
        battleIds = List.copyOf(battleIds);
    }
}
