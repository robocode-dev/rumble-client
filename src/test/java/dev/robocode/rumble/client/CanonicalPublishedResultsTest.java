package dev.robocode.rumble.client;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.NoSuchFileException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("RCL-006")
class CanonicalPublishedResultsTest {
    private static final URI REPOSITORY = URI.create("https://github.com/example/rumble-data");
    private static final RankedBattleRecord RECORD = IssueOpsSubmissionTest.record("676b8646-5993-4b60-9a1e-6112530d1e8d");

    @Test
    void confirmsIdenticalRawFactOrCompactedRollup() throws IOException {
        final Map<String, String> files = new HashMap<>();
        files.put("results/raw/2026/08/hash.json", fact().toString());
        assertEquals(Set.of(RECORD.battleId()), verifier(files).find(REPOSITORY, List.of(RECORD), "alice"));
        files.clear();
        files.put("results/rollups/2026/08.json", "{\"results\":[" + fact() + "]}");
        assertEquals(Set.of(RECORD.battleId()), verifier(files).find(REPOSITORY, List.of(RECORD), "alice"));
    }

    @Test
    void missingForeignOrChangedFactCannotAcknowledgeRecord() throws IOException {
        final Map<String, String> files = new HashMap<>();
        assertTrue(verifier(files).find(REPOSITORY, List.of(RECORD), "alice").isEmpty());
        final JsonObject foreign = fact();
        foreign.addProperty("submittedBy", "mallory");
        files.put("results/raw/2026/08/hash.json", foreign.toString());
        assertTrue(verifier(files).find(REPOSITORY, List.of(RECORD), "alice").isEmpty());
        final JsonObject changed = fact();
        changed.addProperty("selectionSeed", 999);
        files.put("results/raw/2026/08/hash.json", changed.toString());
        assertTrue(verifier(files).find(REPOSITORY, List.of(RECORD), "alice").isEmpty());
    }

    private static JsonObject fact() {
        final JsonObject fact = RankedJournal.recordJson(RECORD);
        fact.remove("schemaVersion");
        fact.addProperty("submittedBy", "alice");
        fact.addProperty("payloadHash", "sha256:hash");
        return fact;
    }

    private static CanonicalPublishedResults verifier(final Map<String, String> files) {
        return new CanonicalPublishedResults(repository -> new RepositoryReader.RepositoryCheckout() {
            public URI repository() { return repository; }
            public String revision() { return "a".repeat(40); }
            public String read(final String path) throws IOException {
                if (!files.containsKey(path)) { throw new NoSuchFileException(path); }
                return files.get(path);
            }
            public List<String> listFiles(final String directory) {
                return files.keySet().stream().filter(path -> path.startsWith(directory + "/")).toList();
            }
            public void close() { }
        });
    }
}
