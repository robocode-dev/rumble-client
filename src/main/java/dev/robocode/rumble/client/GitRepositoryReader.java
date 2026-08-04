package dev.robocode.rumble.client;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads one remote repository revision through an isolated shallow Git clone.
 */
final class GitRepositoryReader implements RepositoryReader {
    @Override
    public RepositoryCheckout checkout(final URI repository) throws IOException {
        final Path directory = Files.createTempDirectory("rumble-client-repository-");
        try {
            runGit("clone", "--quiet", "--depth", "1", "--no-tags", repository.toString(), directory.toString());
            final String revision = runGit("-C", directory.toString(), "rev-parse", "HEAD").trim();
            return new Checkout(repository, directory, revision);
        } catch (IOException exception) {
            deleteTree(directory);
            throw exception;
        }
    }

    @Override
    public RepositoryCheckout checkout(final URI repository, final String revision) throws IOException {
        final Path directory = Files.createTempDirectory("rumble-client-repository-");
        try {
            runGit("init", "--quiet", directory.toString());
            runGit("-C", directory.toString(), "remote", "add", "origin", repository.toString());
            runGit("-C", directory.toString(), "fetch", "--quiet", "--depth", "1", "origin", revision);
            runGit("-C", directory.toString(), "checkout", "--quiet", "--detach", "FETCH_HEAD");
            final String actualRevision = runGit("-C", directory.toString(), "rev-parse", "HEAD").trim();
            if (!actualRevision.equals(revision)) {
                throw new IOException("Repository returned " + actualRevision + " for requested commit " + revision);
            }
            return new Checkout(repository, directory, actualRevision);
        } catch (IOException exception) {
            deleteTree(directory);
            throw exception;
        }
    }

    private static String runGit(final String... arguments) throws IOException {
        final Process process = new ProcessBuilder(prependGit(arguments)).redirectErrorStream(true).start();
        final String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            final int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Git command failed with exit code " + exitCode + ": " + output.strip());
            }
            return output;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for Git", exception);
        }
    }

    private static String[] prependGit(final String[] arguments) {
        final String[] command = new String[arguments.length + 1];
        command[0] = "git";
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        return command;
    }

    private static void deleteTree(final Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (final Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (!Files.isSymbolicLink(path)) {
                    path.toFile().setWritable(true);
                }
                Files.deleteIfExists(path);
            }
        }
    }

    private record Checkout(URI repository, Path directory, String revision) implements RepositoryCheckout {
        @Override
        public String read(final String relativePath) throws IOException {
            return Files.readString(resolveInsideCheckout(relativePath));
        }

        @Override
        public List<String> listFiles(final String relativeDirectory) throws IOException {
            final Path directoryPath = resolveInsideCheckout(relativeDirectory);
            try (Stream<Path> paths = Files.list(directoryPath)) {
                return paths.filter(Files::isRegularFile)
                        .map(path -> directory.relativize(path).toString().replace('\\', '/'))
                        .sorted()
                        .toList();
            }
        }

        @Override
        public void copyDirectory(final String relativeDirectory, final Path destination) throws IOException {
            final Path source = resolveInsideCheckout(relativeDirectory);
            if (!Files.isDirectory(source)) {
                throw new IOException("Repository path is not a directory: " + relativeDirectory);
            }
            try (Stream<Path> paths = Files.walk(source)) {
                for (final Path path : paths.sorted().toList()) {
                    if (Files.isSymbolicLink(path)) {
                        throw new IOException("Bot source must not contain symbolic links: " + relativeDirectory);
                    }
                    final Path target = destination.resolve(source.relativize(path).toString());
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(target);
                    } else if (Files.isRegularFile(path)) {
                        Files.copy(path, target, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                }
            }
        }

        private Path resolveInsideCheckout(final String relativePath) throws IOException {
            final Path requested = Path.of(relativePath);
            if (requested.isAbsolute()) {
                throw new IOException("Repository path must be relative: " + relativePath);
            }
            final Path root = directory.toRealPath();
            final Path resolved = directory.resolve(requested).normalize().toRealPath();
            if (!resolved.startsWith(root)) {
                throw new IOException("Repository path escapes checkout: " + relativePath);
            }
            return resolved;
        }

        @Override
        public void close() throws IOException {
            deleteTree(directory);
        }
    }
}
