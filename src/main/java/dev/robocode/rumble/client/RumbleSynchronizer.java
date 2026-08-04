package dev.robocode.rumble.client;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;

/**
 * Resolves the canonical data repository and validates one ranked input snapshot.
 */
final class RumbleSynchronizer {
    private static final int MAX_CANONICAL_HOPS = 5;

    private final RepositoryReader repositoryReader;
    private final RumbleSnapshotParser parser;

    RumbleSynchronizer(final RepositoryReader repositoryReader) {
        this(repositoryReader, new RumbleSnapshotParser());
    }

    RumbleSynchronizer(final RepositoryReader repositoryReader, final RumbleSnapshotParser parser) {
        this.repositoryReader = repositoryReader;
        this.parser = parser;
    }

    RumbleSnapshot synchronize(final ClientConfiguration configuration) throws IOException {
        if (configuration.mode() != ClientMode.RANKED) {
            throw new IllegalArgumentException("Ranked synchronization requires ranked mode");
        }
        try (RepositoryReader.RepositoryCheckout checkout = openCanonical(configuration.dataRepository())) {
            return parser.parse(checkout, configuration);
        }
    }

    private RepositoryReader.RepositoryCheckout openCanonical(final URI initialRepository) throws IOException {
        final Set<String> visited = new HashSet<>();
        URI current = initialRepository;
        for (int hop = 0; hop < MAX_CANONICAL_HOPS; hop++) {
            final String identity = repositoryIdentity(current);
            if (!visited.add(identity)) {
                throw new IllegalArgumentException("Canonical repository pointer contains a cycle at " + current);
            }
            final RepositoryReader.RepositoryCheckout checkout = repositoryReader.checkout(current);
            try {
                final JsonContract pointer = JsonContract.parse(checkout.read("wellknown/rumble.json"),
                        "wellknown/rumble.json");
                final URI canonical = pointer.httpsUri("canonical");
                final String movedTo = pointer.nullableString("movedTo");
                final URI target = movedTo == null ? canonical : parseHttpsUri(movedTo, "wellknown/rumble.json.movedTo");
                if (repositoryIdentity(target).equals(identity)) {
                    return checkout;
                }
                current = target;
            } catch (IOException | RuntimeException exception) {
                checkout.close();
                throw exception;
            }
            checkout.close();
        }
        throw new IllegalArgumentException("Canonical repository pointer exceeds " + MAX_CANONICAL_HOPS + " hops");
    }

    private static URI parseHttpsUri(final String value, final String field) {
        final JsonContract wrapper = JsonContract.parse(
                "{\"schemaVersion\":1,\"value\":\"" + escapeJson(value) + "\"}", field);
        return wrapper.httpsUri("value");
    }

    private static String escapeJson(final String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String repositoryIdentity(final URI repository) {
        final URI normalized = repository.normalize();
        String path = normalized.getPath();
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.endsWith(".git")) {
            path = path.substring(0, path.length() - 4);
        }
        final int port = normalized.getPort();
        return normalized.getScheme().toLowerCase(java.util.Locale.ROOT) + "://"
                + normalized.getHost().toLowerCase(java.util.Locale.ROOT)
                + (port < 0 ? "" : ":" + port) + path;
    }
}
