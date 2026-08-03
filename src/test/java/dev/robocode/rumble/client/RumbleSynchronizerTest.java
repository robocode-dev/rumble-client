package dev.robocode.rumble.client;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RumbleSynchronizerTest {
    private static final URI PREVIOUS_REPOSITORY = URI.create("https://github.com/example/previous-data");
    private static final URI CANONICAL_REPOSITORY = URI.create("https://github.com/example/rumble-data");
    private static final URI BOTS_REPOSITORY = URI.create("https://github.com/example/rumble-bots");

    @Test
    @Tag("RCL-002")
    void testRCL002_IntegrationPositive_followsCanonicalPointerAndValidatesSnapshot() throws IOException {
        final InMemoryRepositoryReader repositories = validRepositories();

        final RumbleSnapshot snapshot = new RumbleSynchronizer(repositories).synchronize(configuration());

        assertEquals(CANONICAL_REPOSITORY, snapshot.canonicalDataRepository());
        assertEquals("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", snapshot.dataRevision());
        assertEquals(1, snapshot.engine().behaviorVersion());
        assertEquals("alice", snapshot.registration().account());
        assertEquals(List.of(PREVIOUS_REPOSITORY, CANONICAL_REPOSITORY), repositories.requestedRepositories());
        assertEquals(1, snapshot.advice().get(GameType.ONE_VS_ONE).priorityPairs().size());
    }

    @Test
    @Tag("RCL-002")
    void testRCL002_IntegrationNegative_rejectsUnknownAdviceSchemaBeforeReturningSnapshot() {
        final InMemoryRepositoryReader repositories = validRepositories();
        repositories.replace(CANONICAL_REPOSITORY, "matchmaking/matches_needed-1v1.json",
                validAdvice().replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"));

        assertThrows(IllegalArgumentException.class,
                () -> new RumbleSynchronizer(repositories).synchronize(configuration()));
    }

    @Test
    @Tag("RCL-002")
    void testRCL002_IntegrationNegative_rejectsUnregisteredClientIdentity() {
        final InMemoryRepositoryReader repositories = validRepositories();
        repositories.replace(CANONICAL_REPOSITORY, "clients/alice.json",
                """
                {"schemaVersion": 1, "account": "alice", "clientIds": ["another-client"]}
                """);

        assertThrows(IllegalArgumentException.class,
                () -> new RumbleSynchronizer(repositories).synchronize(configuration()));
    }

    @Test
    @Tag("RCL-002")
    void testRCL002_IntegrationNegative_rejectsCatalogFromDifferentBotRepository() {
        final InMemoryRepositoryReader repositories = validRepositories();
        repositories.replace(CANONICAL_REPOSITORY, "catalog.json",
                repositories.read(CANONICAL_REPOSITORY, "catalog.json")
                        .replace("raw.githubusercontent.com/example/rumble-bots",
                                "raw.githubusercontent.com/example/unreviewed-bots"));

        assertThrows(IllegalArgumentException.class,
                () -> new RumbleSynchronizer(repositories).synchronize(configuration()));
    }

    private static ClientConfiguration configuration() {
        return new ClientConfiguration(BOTS_REPOSITORY, PREVIOUS_REPOSITORY, "alice-desktop", Set.of(),
                Set.of(GameType.ONE_VS_ONE), 10, ClientMode.RANKED, Path.of("work"));
    }

    private static InMemoryRepositoryReader validRepositories() {
        final InMemoryRepositoryReader repositories = new InMemoryRepositoryReader();
        repositories.add(PREVIOUS_REPOSITORY, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", Map.of(
                "wellknown/rumble.json", """
                        {"schemaVersion": 1, "canonical": "https://github.com/example/previous-data", "movedTo": "https://github.com/example/rumble-data"}
                        """));
        final Map<String, String> canonicalFiles = new LinkedHashMap<>();
        canonicalFiles.put("wellknown/rumble.json", """
                {"schemaVersion": 1, "canonical": "https://github.com/example/rumble-data", "movedTo": null}
                """);
        canonicalFiles.put("engine.json", """
                {
                  "schemaVersion": 1,
                  "behaviorVersion": 1,
                  "tankRoyaleVersion": "unreleased",
                  "image": "ghcr.io/example/tank-royale:unreleased",
                  "gameTypes": {"1v1": {"rounds": 35, "battlefield": [800, 600], "participants": 2}}
                }
                """);
        canonicalFiles.put("catalog.json", """
                {
                  "schemaVersion": 1,
                  "source": "https://raw.githubusercontent.com/example/rumble-bots/main/bots/index.json",
                  "sourceCommit": "cccccccccccccccccccccccccccccccccccccccc",
                  "bots": [
                    {"name": "Alpha", "version": "1.0", "platform": "Java", "path": "bots/java/Alpha", "sourceHash": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "status": "active"},
                    {"name": "Bravo", "version": "1.0", "platform": "Python", "path": "bots/python/Bravo", "sourceHash": "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "status": "active"}
                  ]
                }
                """);
        canonicalFiles.put("clients/alice.json", """
                {"schemaVersion": 1, "account": "alice", "clientIds": ["alice-desktop"]}
                """);
        canonicalFiles.put("matchmaking/matches_needed-1v1.json", validAdvice());
        repositories.add(CANONICAL_REPOSITORY, "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", canonicalFiles);
        return repositories;
    }

    private static String validAdvice() {
        return """
                {
                  "schemaVersion": 1,
                  "projectionId": "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
                  "gameType": "1v1",
                  "targetSamplesPerPairing": 6,
                  "priorityPairs": [{"bots": ["Alpha 1.0", "Bravo 1.0"], "have": 0, "reason": "new-bot"}]
                }
                """;
    }

    private static final class InMemoryRepositoryReader implements RepositoryReader {
        private final Map<URI, RepositoryData> repositories = new LinkedHashMap<>();
        private final List<URI> requestedRepositories = new ArrayList<>();

        void add(final URI repository, final String revision, final Map<String, String> files) {
            repositories.put(repository, new RepositoryData(revision, new LinkedHashMap<>(files)));
        }

        void replace(final URI repository, final String path, final String content) {
            repositories.get(repository).files().put(path, content);
        }

        List<URI> requestedRepositories() {
            return List.copyOf(requestedRepositories);
        }

        String read(final URI repository, final String path) {
            return repositories.get(repository).files().get(path);
        }

        @Override
        public RepositoryCheckout checkout(final URI repository) throws IOException {
            final RepositoryData data = repositories.get(repository);
            if (data == null) {
                throw new IOException("Unknown repository " + repository);
            }
            requestedRepositories.add(repository);
            return new RepositoryCheckout() {
                @Override
                public URI repository() {
                    return repository;
                }

                @Override
                public String revision() {
                    return data.revision();
                }

                @Override
                public String read(final String relativePath) throws IOException {
                    final String content = data.files().get(relativePath);
                    if (content == null) {
                        throw new IOException("Missing " + relativePath);
                    }
                    return content;
                }

                @Override
                public List<String> listFiles(final String relativeDirectory) {
                    final String prefix = relativeDirectory + "/";
                    return data.files().keySet().stream()
                            .filter(path -> path.startsWith(prefix) && !path.substring(prefix.length()).contains("/"))
                            .sorted()
                            .toList();
                }

                @Override
                public void close() {
                }
            };
        }
    }

    private record RepositoryData(String revision, Map<String, String> files) {
    }
}
