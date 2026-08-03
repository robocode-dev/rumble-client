package dev.robocode.rumble.client;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * Opens immutable repository views for one synchronization attempt.
 */
interface RepositoryReader {
    RepositoryCheckout checkout(URI repository) throws IOException;

    /**
     * A read-only repository revision. Implementations are not required to be thread-safe.
     */
    interface RepositoryCheckout extends AutoCloseable {
        URI repository();

        String revision();

        String read(String relativePath) throws IOException;

        List<String> listFiles(String relativeDirectory) throws IOException;

        @Override
        void close() throws IOException;
    }
}
