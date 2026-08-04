package dev.robocode.rumble.client;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

/**
 * Opens immutable repository views for one synchronization attempt.
 */
interface RepositoryReader {
    RepositoryCheckout checkout(URI repository) throws IOException;

    default RepositoryCheckout checkout(final URI repository, final String revision) throws IOException {
        final RepositoryCheckout checkout = checkout(repository);
        if (!checkout.revision().equals(revision)) {
            checkout.close();
            throw new IOException("Repository revision does not match requested commit " + revision);
        }
        return checkout;
    }

    /**
     * A read-only repository revision. Implementations are not required to be thread-safe.
     */
    interface RepositoryCheckout extends AutoCloseable {
        URI repository();

        String revision();

        String read(String relativePath) throws IOException;

        List<String> listFiles(String relativeDirectory) throws IOException;

        default void copyDirectory(final String relativeDirectory, final Path destination) throws IOException {
            throw new IOException("Repository checkout does not support directory export");
        }

        @Override
        void close() throws IOException;
    }
}
