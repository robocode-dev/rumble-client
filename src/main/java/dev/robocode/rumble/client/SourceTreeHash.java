package dev.robocode.rumble.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * Computes the canonical source-tree identity shared with the Rumble bot catalog.
 */
final class SourceTreeHash {
    private SourceTreeHash() {
    }

    static String sha256(final Path directory) throws IOException {
        final MessageDigest digest = sha256Digest();
        for (final Path file : sourceFiles(directory)) {
            final String relativePath = directory.relativize(file).toString().replace('\\', '/');
            digest.update(relativePath.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Files.readAllBytes(file));
            digest.update((byte) 0);
        }
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    private static List<Path> sourceFiles(final Path directory) throws IOException {
        if (!Files.isDirectory(directory) || Files.isSymbolicLink(directory)) {
            throw new IOException("Bot cache path is not a regular directory: " + directory);
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            final List<Path> allPaths = paths.sorted(Comparator.comparing(
                    path -> directory.relativize(path).toString().replace('\\', '/'))).toList();
            for (final Path path : allPaths) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("Bot source must not contain symbolic links: " + path);
                }
            }
            return allPaths.stream()
                    .filter(Files::isRegularFile)
                    .filter(path -> !containsPycache(directory.relativize(path)))
                    .toList();
        }
    }

    private static boolean containsPycache(final Path relativePath) {
        for (final Path segment : relativePath) {
            if (segment.toString().equals("__pycache__")) {
                return true;
            }
        }
        return false;
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
