package dev.robocode.rumble.client;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotCachePreparerTest {
    private static final URI BOTS_REPOSITORY = URI.create("https://github.com/example/rumble-bots");
    private static final String SOURCE_COMMIT = "cccccccccccccccccccccccccccccccccccccccc";
    private static final String SOURCE_HASH =
            "sha256:f1fa3ca115fe477cf21020ce2d22c2ea99fba003071b1c17e999102b5d308d5b";

    @TempDir
    Path temporaryDirectory;

    @Test
    @Tag("RCL-002")
    void testRCL002_IntegrationPositive_materializesExactCommitAndValidatesSourceHash() throws IOException {
        final Path sourceRepository = createSourceRepository();
        final TestRepositoryReader repositories = new TestRepositoryReader(sourceRepository, SOURCE_COMMIT);
        final ClientConfiguration configuration = configuration();

        final PreparedBotCache cache = new BotCachePreparer(repositories).prepare(
                snapshot(SOURCE_HASH), configuration);

        final Path cachedBot = configuration.workDirectory().resolve("cache/bots")
                .resolve(SOURCE_COMMIT).resolve("bots/java/Alpha");
        assertEquals(SOURCE_COMMIT, repositories.requestedRevision());
        assertEquals(SOURCE_COMMIT, cache.sourceCommit());
        assertEquals("class Alpha {}\n", Files.readString(cachedBot.resolve("src/Alpha.java")));
        assertEquals(cachedBot, cache.bots().values().iterator().next());

        new BotCachePreparer(repositories).prepare(snapshot(SOURCE_HASH), configuration);
        assertEquals(1, repositories.checkoutCount());
    }

    @Test
    @Tag("RCL-002")
    void testRCL002_IntegrationNegative_rejectsHashMismatchWithoutPublishingPartialCache() throws IOException {
        final TestRepositoryReader repositories = new TestRepositoryReader(createSourceRepository(), SOURCE_COMMIT);
        final ClientConfiguration configuration = configuration();
        final String wrongHash = "sha256:0000000000000000000000000000000000000000000000000000000000000000";

        assertThrows(IllegalArgumentException.class,
                () -> new BotCachePreparer(repositories).prepare(snapshot(wrongHash), configuration));

        assertFalse(Files.exists(configuration.workDirectory().resolve("cache/bots").resolve(SOURCE_COMMIT)));
        try (Stream<Path> entries = Files.list(configuration.workDirectory().resolve("cache/bots"))) {
            assertTrue(entries.findAny().isEmpty());
        }
    }

    private Path createSourceRepository() throws IOException {
        final Path bot = temporaryDirectory.resolve("source/bots/java/Alpha");
        Files.createDirectories(bot.resolve("src"));
        Files.writeString(bot.resolve("Alpha.json"), "{}\n");
        Files.writeString(bot.resolve("src/Alpha.java"), "class Alpha {}\n");
        return temporaryDirectory.resolve("source");
    }

    private ClientConfiguration configuration() {
        return new ClientConfiguration(BOTS_REPOSITORY, URI.create("https://github.com/example/rumble-data"),
                Optional.of("alice-desktop"), Set.of(), Set.of(GameType.ONE_VS_ONE), 1,
                ClientMode.RANKED, temporaryDirectory.resolve("work"));
    }

    private static RumbleSnapshot snapshot(final String sourceHash) {
        final CatalogBot bot = new CatalogBot("Alpha", "1.0", "JVM", "bots/java/Alpha", sourceHash);
        final BotCatalog catalog = new BotCatalog(
                URI.create("https://raw.githubusercontent.com/example/rumble-bots/main/bots/index.json"),
                SOURCE_COMMIT, Map.of(bot.displayName(), bot));
        return new RumbleSnapshot(URI.create("https://github.com/example/rumble-data"),
                "dddddddddddddddddddddddddddddddddddddddd",
                new EnginePin(1, "unreleased", "example", Map.of()), catalog,
                new ClientRegistration("alice", "alice-desktop"), Map.of());
    }

    private static final class TestRepositoryReader implements RepositoryReader {
        private final Path repositoryDirectory;
        private final String revision;
        private String requestedRevision;
        private int checkoutCount;

        private TestRepositoryReader(final Path repositoryDirectory, final String revision) {
            this.repositoryDirectory = repositoryDirectory;
            this.revision = revision;
        }

        String requestedRevision() {
            return requestedRevision;
        }

        int checkoutCount() {
            return checkoutCount;
        }

        @Override
        public RepositoryCheckout checkout(final URI repository) {
            checkoutCount++;
            return new RepositoryCheckout() {
                @Override
                public URI repository() {
                    return repository;
                }

                @Override
                public String revision() {
                    return revision;
                }

                @Override
                public String read(final String relativePath) throws IOException {
                    return Files.readString(repositoryDirectory.resolve(relativePath));
                }

                @Override
                public List<String> listFiles(final String relativeDirectory) throws IOException {
                    try (Stream<Path> paths = Files.list(repositoryDirectory.resolve(relativeDirectory))) {
                        return paths.filter(Files::isRegularFile)
                                .map(path -> repositoryDirectory.relativize(path).toString().replace('\\', '/'))
                                .sorted()
                                .toList();
                    }
                }

                @Override
                public void copyDirectory(final String relativeDirectory, final Path destination) throws IOException {
                    final Path source = repositoryDirectory.resolve(relativeDirectory);
                    try (Stream<Path> paths = Files.walk(source)) {
                        for (final Path path : paths.sorted().toList()) {
                            final Path target = destination.resolve(source.relativize(path).toString());
                            if (Files.isDirectory(path)) {
                                Files.createDirectories(target);
                            } else {
                                Files.copy(path, target, StandardCopyOption.COPY_ATTRIBUTES);
                            }
                        }
                    }
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public RepositoryCheckout checkout(final URI repository, final String requestedCommit) throws IOException {
            requestedRevision = requestedCommit;
            return RepositoryReader.super.checkout(repository, requestedCommit);
        }
    }
}
