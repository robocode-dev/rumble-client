package dev.robocode.rumble.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** GitHub REST adapter limited to finding result issues and reading their receipt comments. Needs no credential. */
final class GitHubIssueOpsTransport implements IssueOpsTransport {
    private static final URI API_ROOT = URI.create("https://api.github.com/");
    private static final String API_VERSION = "2026-03-10";

    private static final Pattern JSON_BLOCK = Pattern.compile("```json\\s*\\n(.*?)\\n```", Pattern.DOTALL);
    private static final long INGESTION_BOT_ID = 41898282L;

    private final HttpClient client;
    private final String token;
    private final URI apiRoot;

    /** Reads public issues; an optional token only raises the anonymous request limit and is never required. */
    GitHubIssueOpsTransport(final String token) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build(), token);
    }

    GitHubIssueOpsTransport(final HttpClient client, final String token) {
        this(client, token, API_ROOT);
    }

    GitHubIssueOpsTransport(final HttpClient client, final String token, final URI apiRoot) {
        this.client = client;
        this.token = token == null || token.isBlank() ? null : token;
        this.apiRoot = apiRoot;
    }

    @Override
    public Optional<SubmittedBatch> findIssue(final URI repository, final PreparedBatch prepared, final String account)
            throws IOException {
        for (int page = 1; ; page++) {
            final JsonArray issues = request("GET", endpoint(repository, "issues?state=all&sort=created&direction=desc"
                    + "&creator=" + URLEncoder.encode(account, StandardCharsets.UTF_8)
                    + "&per_page=100&page=" + page), null).getAsJsonArray("comments");
            for (final JsonElement element : issues) {
                final JsonObject issue = element.getAsJsonObject();
                if (!issue.has("pull_request") && prepared.title().equals(issue.get("title").getAsString())
                        && issue.has("user") && account.equalsIgnoreCase(issue.getAsJsonObject("user").get("login").getAsString())
                        && sameEnvelope(issue.get("body"), prepared.body())) {
                    return Optional.of(new SubmittedBatch(issue.get("number").getAsInt(),
                            issue.get("html_url").getAsString(), prepared.battleIds()));
                }
            }
            if (issues.size() < 100) {
                break;
            }
        }
        return Optional.empty();
    }

    @Override
    public SubmissionStatus status(final URI repository, final SubmittedBatch batch) throws IOException {
        final List<SubmissionReceipt> receipts = new ArrayList<>();
        for (int page = 1; ; page++) {
            final JsonArray comments = request("GET", endpoint(repository, "issues/" + batch.issueNumber()
                    + "/comments?per_page=100&page=" + page), null).getAsJsonArray("comments");
            for (final JsonElement element : comments) {
                final JsonObject comment = element.getAsJsonObject();
                final JsonElement body = comment.get("body");
                final JsonObject user = comment.getAsJsonObject("user");
                // The ingestion workflow publishes these receipts only after pushing canonical facts.
                if (user != null && user.has("id") && user.get("id").getAsLong() == INGESTION_BOT_ID
                        && "github-actions[bot]".equals(user.get("login").getAsString())
                        && "Bot".equals(user.get("type").getAsString())
                        && body != null && body.isJsonPrimitive()) {
                    receipts.addAll(receipts(body.getAsString(), batch.issueUrl()));
                }
            }
            if (comments.size() < 100) {
                break;
            }
        }
        final JsonObject issue = request("GET", endpoint(repository, "issues/" + batch.issueNumber()), null);
        final boolean terminal = "closed".equals(issue.get("state").getAsString());
        return new SubmissionStatus(batch, receipts, terminal);
    }

    private JsonObject request(final String method, final URI endpoint, final String body) throws IOException {
        final HttpRequest.Builder request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", API_VERSION);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
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

    private URI endpoint(final URI repository, final String suffix) {
        if (!"github.com".equalsIgnoreCase(repository.getHost())) {
            throw new IllegalArgumentException("Reading submission receipts requires a github.com canonical repository");
        }
        final String[] segments = repository.getPath().replaceFirst("/$", "").replaceFirst("\\.git$", "")
                .split("/");
        if (segments.length != 3 || segments[1].isBlank() || segments[2].isBlank()) {
            throw new IllegalArgumentException("Canonical repository must identify a GitHub owner and repository");
        }
        return apiRoot.resolve("repos/" + segments[1] + "/" + segments[2] + "/" + suffix);
    }

    private static boolean sameEnvelope(final JsonElement body, final String preparedBody) {
        if (body == null || body.isJsonNull() || !body.isJsonPrimitive()) {
            return false;
        }
        try {
            final var actual = JSON_BLOCK.matcher(body.getAsString());
            final var expected = JSON_BLOCK.matcher(preparedBody);
            if (!actual.find() || !expected.find()) {
                return false;
            }
            final JsonElement envelope = JsonParser.parseString(actual.group(1));
            return !actual.find() && JsonContract.sameValue(envelope, JsonParser.parseString(expected.group(1)));
        } catch (RuntimeException exception) {
            return false;
        }
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
