package dev.robocode.rumble.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@Tag("RCL-006")
class GitHubIssueOpsTransportTest {
    private static final URI REPOSITORY = URI.create("https://github.com/example/rumble-data");
    private static final UUID ID = UUID.fromString("676b8646-5993-4b60-9a1e-6112530d1e8d");
    private static final PreparedBatch PREPARED = new PreparedBatch("[result] alice-client stamp",
            "```json\n{\"results\":[{\"battleId\":\"" + ID + "\"}]}\n```", List.of(ID));

    @Test
    void anonymousDiscoveryContinuesPastThreePagesAndChecksAccountAndEnvelope() throws Exception {
        final List<String> requests = new ArrayList<>();
        try (final Api api = new Api(path -> {
            requests.add(path);
            final JsonArray issues = new JsonArray();
            if (!path.contains("&page=4")) {
                issues.add(issue("mallory", PREPARED.body()));
                issues.add(issue("alice", "```json\n{}\n```"));
                final JsonObject pullRequest = issue("alice", PREPARED.body());
                pullRequest.add("pull_request", new JsonObject());
                issues.add(pullRequest);
                while (issues.size() < 100) {
                    issues.add(issue("alice", null));
                }
            } else {
                issues.add(issue("alice", "### Batch\n\n" + PREPARED.body()));
            }
            return issues.toString();
        })) {
            final SubmittedBatch batch = api.transport().findIssue(REPOSITORY, PREPARED, "alice").orElseThrow();
            assertEquals(42, batch.issueNumber());
            assertEquals(List.of(ID), batch.battleIds());
            assertEquals(4, requests.size());
            assertTrue(requests.stream().allMatch(path -> path.contains("creator=alice")));
            assertTrue(api.authorizations.stream().allMatch(value -> value == null));
        }
    }

    @Test
    void onlyWorkflowBotReceiptsAreAcceptedIncludingOnLaterCommentPages() throws Exception {
        try (final Api api = new Api(path -> {
            if (!path.contains("comments")) {
                return "{\"state\":\"closed\"}";
            }
            final JsonArray comments = new JsonArray();
            if (path.contains("&page=1")) {
                for (int index = 0; index < 100; index++) {
                    comments.add(comment("alice", 123, "User"));
                }
            } else {
                comments.add(comment("github-actions[bot]", 123, "Bot"));
                comments.add(comment("github-actions[bot]", 41898282, "Bot"));
            }
            return comments.toString();
        })) {
            final SubmissionStatus status = api.transport().status(REPOSITORY, batch());
            assertEquals(List.of(new SubmissionReceipt(ID, batch().issueUrl())), status.receipts());
            assertTrue(status.terminal());
        }
    }

    @Test
    void ordinaryAcceptedLookingCommentDoesNotProduceReceipt() throws Exception {
        try (final Api api = new Api(path -> path.contains("comments")
                ? "[" + comment("alice", 123, "User") + "]" : "{\"state\":\"open\"}")) {
            assertTrue(api.transport().status(REPOSITORY, batch()).receipts().isEmpty());
        }
    }

    @Test
    void anonymousRateLimitFailsExplicitlyAndCanBeRetried() throws Exception {
        try (final Api api = new Api(path -> "[]")) {
            api.status = 403;
            final IOException failure = assertThrows(IOException.class,
                    () -> api.transport().findIssue(REPOSITORY, PREPARED, "alice"));
            assertTrue(failure.getMessage().contains("403"));
            api.status = 200;
            assertTrue(api.transport().findIssue(REPOSITORY, PREPARED, "alice").isEmpty());
        }
    }

    private static SubmittedBatch batch() {
        return new SubmittedBatch(42, REPOSITORY + "/issues/42", List.of(ID));
    }

    private static JsonObject issue(final String account, final String body) {
        final JsonObject issue = new JsonObject();
        issue.addProperty("title", PREPARED.title());
        issue.addProperty("number", 42);
        issue.addProperty("html_url", batch().issueUrl());
        issue.addProperty("body", body);
        final JsonObject user = new JsonObject();
        user.addProperty("login", account);
        issue.add("user", user);
        return issue;
    }

    private static JsonObject comment(final String login, final long id, final String type) {
        final JsonObject comment = new JsonObject();
        comment.addProperty("body", "Processed result batch:\n\n```text\n" + ID + ": accepted\n```");
        final JsonObject user = new JsonObject();
        user.addProperty("login", login);
        user.addProperty("id", id);
        user.addProperty("type", type);
        comment.add("user", user);
        return comment;
    }

    private static final class Api implements AutoCloseable {
        private final HttpServer server;
        private final List<String> authorizations = new ArrayList<>();
        private volatile int status = 200;

        Api(final Function<String, String> response) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
                final byte[] bytes = response.apply(exchange.getRequestURI().toString()).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
            });
            server.start();
        }

        GitHubIssueOpsTransport transport() {
            return new GitHubIssueOpsTransport(HttpClient.newHttpClient(), null,
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
