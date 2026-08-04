package dev.robocode.rumble.client;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Materializes and validates the immutable bot sources pinned by a ranked snapshot.
 */
final class BotCachePreparer {
    private final RepositoryReader repositoryReader;

    BotCachePreparer(final RepositoryReader repositoryReader) {
        this.repositoryReader = repositoryReader;
    }

    PreparedBotCache prepare(final RumbleSnapshot snapshot, final ClientConfiguration configuration)
            throws IOException {
        if (configuration.mode() != ClientMode.RANKED) {
            throw new IllegalArgumentException("Bot cache preparation requires ranked mode");
        }
        final String sourceCommit = snapshot.catalog().sourceCommit();
        final Path cacheParent = configuration.workDirectory().resolve("cache/bots");
        final Path cacheDirectory = cacheParent.resolve(sourceCommit);
        if (Files.exists(cacheDirectory)) {
            return validateCache(snapshot.catalog(), cacheDirectory);
        }

        Files.createDirectories(cacheParent);
        final Path stagingDirectory = Files.createTempDirectory(cacheParent, sourceCommit + "-");
        try {
            try (RepositoryReader.RepositoryCheckout checkout = repositoryReader.checkout(
                    configuration.botsRepository(), sourceCommit)) {
                for (final CatalogBot bot : sortedBots(snapshot.catalog())) {
                    checkout.copyDirectory(bot.path(), stagingDirectory.resolve(bot.path()));
                }
            }
            validateCache(snapshot.catalog(), stagingDirectory);
            publish(stagingDirectory, cacheDirectory);
            return validateCache(snapshot.catalog(), cacheDirectory);
        } finally {
            deleteTree(stagingDirectory);
        }
    }

    private static PreparedBotCache validateCache(final BotCatalog catalog, final Path cacheDirectory)
            throws IOException {
        final Map<CatalogBot, Path> paths = new LinkedHashMap<>();
        for (final CatalogBot bot : sortedBots(catalog)) {
            final Path botDirectory = cacheDirectory.resolve(bot.path()).normalize();
            if (!botDirectory.startsWith(cacheDirectory)) {
                throw new IOException("Bot cache path escapes its source commit: " + bot.path());
            }
            final String actualHash = SourceTreeHash.sha256(botDirectory);
            if (!actualHash.equals(bot.sourceHash())) {
                throw new IllegalArgumentException("Bot source hash mismatch for " + bot.displayName()
                        + ": expected " + bot.sourceHash() + " but found " + actualHash);
            }
            paths.put(bot, botDirectory);
        }
        return new PreparedBotCache(catalog.sourceCommit(), paths);
    }

    private static List<CatalogBot> sortedBots(final BotCatalog catalog) {
        return catalog.activeBots().values().stream()
                .sorted(Comparator.comparing(CatalogBot::displayName))
                .toList();
    }

    private static void publish(final Path stagingDirectory, final Path cacheDirectory) throws IOException {
        try {
            Files.move(stagingDirectory, cacheDirectory, StandardCopyOption.ATOMIC_MOVE);
            return;
        } catch (AtomicMoveNotSupportedException ignored) {
            // The staging directory has the same parent, so a regular move remains safely scoped.
        } catch (FileAlreadyExistsException exception) {
            validateExistingDirectory(cacheDirectory);
            return;
        }
        try {
            Files.move(stagingDirectory, cacheDirectory);
        } catch (FileAlreadyExistsException exception) {
            validateExistingDirectory(cacheDirectory);
        }
    }

    private static void validateExistingDirectory(final Path cacheDirectory) throws IOException {
        if (!Files.isDirectory(cacheDirectory) || Files.isSymbolicLink(cacheDirectory)) {
            throw new IOException("Bot cache commit path is not a regular directory: " + cacheDirectory);
        }
    }

    private static void deleteTree(final Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (final Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}

record PreparedBotCache(String sourceCommit, Map<CatalogBot, Path> bots) {
    PreparedBotCache {
        bots = Map.copyOf(bots);
    }
}
