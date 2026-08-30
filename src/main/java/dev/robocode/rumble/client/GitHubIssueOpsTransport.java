package dev.robocode.rumble.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** GitHub REST adapter limited to creating result issues and reading their receipt comments. */
final class GitHubIssueOpsTransport implements IssueOpsTransport {
    private static final URI API_ROOT = URI.create("https://api.github.com/");
    private static final String API_VERSION = "2026-03-10";

    private final HttpClient client;
    private final String token;

    GitHubIssueOpsTransport(final String token) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build(), token);
    }

    GitHubIssueOpsTransport(final HttpClient client, final String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("RUMBLE_CLIENT_TOKEN must contain an Issues-only GitHub token");
        }
        this.client = client;
        this.token = token;
    }

    @Override
    public SubmittedBatch createIssue(final URI repository, final String body, final String title) throws IOException {
        final JsonObject request = new JsonObject();
        request.addProperty("title", title);
        request.addProperty("body", body);
        final JsonArray labels = new JsonArray();
        labels.add("result-submission");
        request.add("labels", labels);
        final JsonObject response = request("POST", endpoint(repository, "issues"), request.toString());
        final int issueNumber = response.get("number").getAsInt();
        final String issueUrl = response.get("html_url").getAsString();
        return new SubmittedBatch(issueNumber, issueUrl, battleIds(body));
    }

    @Override
    public List<SubmissionReceipt> receipts(final URI repository, final SubmittedBatch batch) throws IOException {
        final JsonArray comments = request("GET", endpoint(repository, "issues/" + batch.issueNumber()
                + "/comments?per_page=100"), null).getAsJsonArray("comments");
        final List<SubmissionReceipt> receipts = new ArrayList<>();
        for (final JsonElement comment : comments) {
            final JsonElement body = comment.getAsJsonObject().get("body");
            if (body != null && body.isJsonPrimitive()) {
                receipts.addAll(receipts(body.getAsString(), batch.issueUrl()));
            }
        }
        return receipts;
    }

    private JsonObject request(final String method, final URI endpoint, final String body) throws IOException {
        final HttpRequest.Builder request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + token)
                .header("X-GitHub-Api-Version", API_VERSION);
        if (body == null) {
            request.GET();
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        try {
            final HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("GitHub Issues API returned HTTP " + response.statusCode());
            }
            final JsonElement parsed = JsonParser.parseString(response.body());
            if (parsed.isJsonObject()) {
                return parsed.getAsJsonObject();
            }
            final JsonObject wrapper = new JsonObject();
            wrapper.add("comments", parsed.getAsJsonArray());
            return wrapper;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while calling the GitHub Issues API", exception);
        } catch (RuntimeException exception) {
            throw new IOException("GitHub Issues API returned invalid JSON", exception);
        }
    }

    private static URI endpoint(final URI repository, final String suffix) {
        if (!"github.com".equalsIgnoreCase(repository.getHost())) {
            throw new IllegalArgumentException("Issues-only submission requires a github.com canonical repository");
        }
        final String[] segments = repository.getPath().replaceFirst("/$", "").replaceFirst("\\.git$", "")
                .split("/");
        if (segments.length != 3 || segments[1].isBlank() || segments[2].isBlank()) {
            throw new IllegalArgumentException("Canonical repository must identify a GitHub owner and repository");
        }
        return API_ROOT.resolve("repos/" + segments[1] + "/" + segments[2] + "/" + suffix);
    }

    private static List<UUID> battleIds(final String body) {
        final int begin = body.indexOf('{');
        final int end = body.lastIndexOf('}');
        final JsonArray results = JsonParser.parseString(body.substring(begin, end + 1)).getAsJsonObject()
                .getAsJsonArray("results");
        return results.asList().stream().map(result -> UUID.fromString(result.getAsJsonObject()
                .get("battleId").getAsString())).toList();
    }

    private static List<SubmissionReceipt> receipts(final String comment, final String issueUrl) {
        return comment.lines().map(String::trim).filter(line -> line.endsWith(": accepted"))
                .map(line -> line.substring(0, line.length() - ": accepted".length()))
                .flatMap(value -> {
                    try {
                        return java.util.stream.Stream.of(new SubmissionReceipt(UUID.fromString(value), issueUrl));
                    } catch (IllegalArgumentException exception) {
                        return java.util.stream.Stream.empty();
                    }
                }).toList();
    }
}
