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
 * Materializes and validates the immutable bot sources pinned by a snapshot, for ranked or practice mode alike.
 */
final class BotCachePreparer {
    private final RepositoryReader repositoryReader;

    BotCachePreparer(final RepositoryReader repositoryReader) {
        this.repositoryReader = repositoryReader;
    }

    PreparedBotCache prepare(final RumbleSnapshot snapshot, final ClientConfiguration configuration)
            throws IOException {
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
            publishTeamMemberAliases(snapshot.catalog(), stagingDirectory);
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

    /**
     * Tank Royale's own team lookup resolves each team member as a sibling directory of the
     * team's own directory, named exactly by the member's display identity (e.g. "Corners
     * 1.0.0"). The Rumble catalog names bot directories by name only, since only one version is
     * ever checked out, so publish a renamed alias copy of each referenced member alongside every
     * team that references it.
     */
    private static void publishTeamMemberAliases(final BotCatalog catalog, final Path stagingDirectory)
            throws IOException {
        for (final CatalogBot team : sortedBots(catalog)) {
            if (!team.isTeam()) {
                continue;
            }
            final Path parentDirectory = stagingDirectory.resolve(team.path()).getParent();
            for (final String memberIdentity : team.teamMembers()) {
                final CatalogBot member = catalog.activeBots().get(memberIdentity);
                if (member == null) {
                    throw new IllegalArgumentException(
                            "Unknown team member `" + memberIdentity + "` for team " + team.displayName());
                }
                final Path aliasDirectory = parentDirectory.resolve(memberIdentity).normalize();
                if (!aliasDirectory.startsWith(parentDirectory)) {
                    throw new IOException("Team member alias escapes its team's directory: " + memberIdentity);
                }
                if (!Files.exists(aliasDirectory)) {
                    copyWithRenamedTopLevelFiles(stagingDirectory.resolve(member.path()), aliasDirectory,
                            member.name(), memberIdentity);
                }
            }
        }
    }

    private static void copyWithRenamedTopLevelFiles(final Path source, final Path destination,
            final String originalName, final String aliasName) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (final Path path : paths.sorted().toList()) {
                final Path relative = source.relativize(path);
                final boolean isRenamableTopLevelFile = relative.getNameCount() == 1
                        && relative.toString().startsWith(originalName + ".");
                final Path target = isRenamableTopLevelFile
                        ? destination.resolve(aliasName + relative.toString().substring(originalName.length()))
                        : destination.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else if (Files.isRegularFile(path)) {
                    Files.copy(path, target, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
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
