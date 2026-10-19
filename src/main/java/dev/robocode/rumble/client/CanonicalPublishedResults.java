package dev.robocode.rumble.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.nio.file.NoSuchFileException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Confirms a receipt against the identical fact in a fresh canonical repository revision. */
final class CanonicalPublishedResults implements PublishedResults {
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("uuuu/MM").withZone(ZoneOffset.UTC);
    private final RepositoryReader repositories;

    CanonicalPublishedResults(final RepositoryReader repositories) {
        this.repositories = repositories;
    }

    @Override
    public Set<UUID> find(final URI repository, final List<RankedBattleRecord> records, final String account)
            throws IOException {
        final Set<UUID> published = new HashSet<>();
        try (var checkout = repositories.checkout(repository)) {
            final Set<String> months = new HashSet<>();
            for (final RankedBattleRecord record : records) {
                months.add(MONTH.format(record.completedAt()));
            }
            final List<JsonObject> facts = new ArrayList<>();
            for (final String month : months) {
                try {
                    for (final String path : checkout.listFiles("results/raw/" + month)) {
                        if (path.endsWith(".json")) {
                            facts.add(JsonParser.parseString(checkout.read(path)).getAsJsonObject());
                        }
                    }
                } catch (NoSuchFileException missing) {
                    // Compacted months have a rollup instead of individual raw facts.
                }
                try {
                    final JsonObject rollup = JsonParser.parseString(checkout.read(
                            "results/rollups/" + month + ".json")).getAsJsonObject();
                    rollup.getAsJsonArray("results").forEach(item -> facts.add(item.getAsJsonObject()));
                } catch (NoSuchFileException missing) {
                    // Current months normally have no rollup yet.
                }
            }
            for (final RankedBattleRecord record : records) {
                final JsonObject expected = RankedJournal.recordJson(record);
                expected.remove("schemaVersion");
                for (final JsonObject fact : facts) {
                    if (fact.has("submittedBy") && account.equalsIgnoreCase(fact.get("submittedBy").getAsString())
                            && expected.entrySet().stream().allMatch(entry -> entry.getValue().equals(fact.get(entry.getKey())))) {
                        published.add(record.battleId());
                        break;
                    }
                }
            }
        } catch (RuntimeException exception) {
            throw new IOException("Canonical published results contain invalid JSON", exception);
        }
        return Set.copyOf(published);
    }
}

@FunctionalInterface
interface PublishedResults {
    Set<UUID> find(URI repository, List<RankedBattleRecord> records, String account) throws IOException;
}
