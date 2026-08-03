package dev.robocode.rumble.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates the mutually dependent documents in one Rumble data checkout.
 */
final class RumbleSnapshotParser {
    private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern SHA_256 = Pattern.compile("sha256:[0-9a-f]{64}");
    private static final Pattern PROJECTION_ID = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> ADVICE_REASONS = Set.of("new-bot", "under-sampled");

    RumbleSnapshot parse(final RepositoryReader.RepositoryCheckout checkout,
                         final ClientConfiguration configuration) throws java.io.IOException {
        final EnginePin engine = parseEngine(checkout.read("engine.json"), configuration.gameTypes());
        final BotCatalog catalog = parseCatalog(checkout.read("catalog.json"), configuration.botsRepository());
        final ClientRegistration registration = parseRegistration(checkout, configuration.clientId());
        final Map<GameType, MatchAdvice> advice = new HashMap<>();
        for (final GameType gameType : configuration.gameTypes()) {
            final String path = "matchmaking/matches_needed-" + gameType.contractName() + ".json";
            advice.put(gameType, parseAdvice(checkout.read(path), path, gameType, catalog));
        }
        return new RumbleSnapshot(checkout.repository(), checkout.revision(), engine, catalog, registration, advice);
    }

    private static EnginePin parseEngine(final String json, final Set<GameType> selectedGameTypes) {
        final JsonContract contract = JsonContract.parse(json, "engine.json");
        final int behaviorVersion = contract.integer("behaviorVersion", 1);
        final String tankRoyaleVersion = contract.string("tankRoyaleVersion");
        final String image = contract.string("image");
        final JsonObject gameTypesObject = contract.object("gameTypes");
        final Map<GameType, GameTypeSettings> gameTypes = new HashMap<>();
        for (final GameType gameType : selectedGameTypes) {
            final JsonElement value = gameTypesObject.get(gameType.contractName());
            if (value == null || !value.isJsonObject()) {
                throw JsonContract.invalid("engine.json has no settings for " + gameType.contractName());
            }
            final JsonContract settings = JsonContract.nested(value.getAsJsonObject(),
                    "engine.json.gameTypes." + gameType.contractName());
            final int rounds = settings.integer("rounds", 1);
            final int participants = settings.integer("participants", 2);
            final JsonArray battlefield = settings.array("battlefield");
            if (battlefield.size() != 2) {
                throw JsonContract.invalid("engine.json battlefield must contain width and height");
            }
            final int width = arrayInteger(battlefield, 0, "engine.json battlefield width", 1);
            final int height = arrayInteger(battlefield, 1, "engine.json battlefield height", 1);
            gameTypes.put(gameType, new GameTypeSettings(rounds, width, height, participants));
        }
        return new EnginePin(behaviorVersion, tankRoyaleVersion, image, gameTypes);
    }

    private static BotCatalog parseCatalog(final String json, final URI expectedBotsRepository) {
        final JsonContract contract = JsonContract.parse(json, "catalog.json");
        final URI source = contract.httpsUri("source");
        if (!sourceBelongsToRepository(source, expectedBotsRepository)) {
            throw JsonContract.invalid("catalog.json.source does not belong to the configured bots repository");
        }
        final String sourceCommit = matching(contract.string("sourceCommit"), COMMIT,
                "catalog.json.sourceCommit must be a full lowercase Git commit");
        final Map<String, CatalogBot> activeBots = new HashMap<>();
        for (final JsonElement element : contract.array("bots")) {
            if (!element.isJsonObject()) {
                throw JsonContract.invalid("catalog.json.bots must contain objects");
            }
            final JsonContract bot = JsonContract.nested(element.getAsJsonObject(), "catalog.json bot");
            final String status = bot.string("status");
            if (!status.equals("active")) {
                continue;
            }
            final CatalogBot entry = new CatalogBot(bot.string("name"), bot.string("version"),
                    bot.string("platform"), bot.string("path"), matching(bot.string("sourceHash"), SHA_256,
                    "catalog bot sourceHash must be sha256:<64 lowercase hex>"));
            if (activeBots.putIfAbsent(entry.displayName(), entry) != null) {
                throw JsonContract.invalid("catalog.json contains duplicate active bot " + entry.displayName());
            }
        }
        if (activeBots.isEmpty()) {
            throw JsonContract.invalid("catalog.json contains no active bots");
        }
        return new BotCatalog(source, sourceCommit, activeBots);
    }

    private static ClientRegistration parseRegistration(final RepositoryReader.RepositoryCheckout checkout,
                                                        final String clientId) throws java.io.IOException {
        ClientRegistration match = null;
        for (final String path : checkout.listFiles("clients")) {
            if (!path.endsWith(".json")) {
                continue;
            }
            final JsonContract registration = JsonContract.parse(checkout.read(path), path);
            final String account = registration.string("account");
            if (!path.equals("clients/" + account + ".json")) {
                throw JsonContract.invalid(path + ".account must match its filename");
            }
            final JsonArray clientIds = registration.array("clientIds");
            if (clientIds.isEmpty()) {
                throw JsonContract.invalid(path + ".clientIds must not be empty");
            }
            final Set<String> uniqueClientIds = new HashSet<>();
            for (final JsonElement element : clientIds) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()
                        || element.getAsString().isBlank()) {
                    throw JsonContract.invalid(path + ".clientIds must contain non-blank strings");
                }
                if (!uniqueClientIds.add(element.getAsString())) {
                    throw JsonContract.invalid(path + ".clientIds must not contain duplicates");
                }
                if (element.getAsString().equals(clientId)) {
                    if (match != null) {
                        throw JsonContract.invalid("clientId is registered to more than one account: " + clientId);
                    }
                    match = new ClientRegistration(account, clientId);
                }
            }
        }
        if (match == null) {
            throw JsonContract.invalid("clientId is not registered: " + clientId);
        }
        return match;
    }

    private static MatchAdvice parseAdvice(final String json, final String path, final GameType expectedGameType,
                                           final BotCatalog catalog) {
        final JsonContract contract = JsonContract.parse(json, path);
        final GameType actualGameType = GameType.fromContractName(contract.string("gameType"));
        if (actualGameType != expectedGameType) {
            throw JsonContract.invalid(path + " declares the wrong gameType");
        }
        final String projectionId = matching(contract.string("projectionId"), PROJECTION_ID,
                path + ".projectionId must be 64 lowercase hex characters");
        final int target = contract.integer("targetSamplesPerPairing", 1);
        final List<PriorityPair> pairs = new ArrayList<>();
        final Set<String> uniquePairs = new HashSet<>();
        for (final JsonElement element : contract.array("priorityPairs")) {
            if (!element.isJsonObject()) {
                throw JsonContract.invalid(path + ".priorityPairs must contain objects");
            }
            final JsonContract pair = JsonContract.nested(element.getAsJsonObject(), path + " priority pair");
            final JsonArray bots = pair.array("bots");
            if (bots.size() != 2) {
                throw JsonContract.invalid(path + " priority pair must identify two bots");
            }
            final List<CatalogBot> catalogBots = new ArrayList<>();
            for (final JsonElement bot : bots) {
                if (!bot.isJsonPrimitive() || !bot.getAsJsonPrimitive().isString()) {
                    throw JsonContract.invalid(path + " priority pair bot must be a string");
                }
                final CatalogBot catalogBot = catalog.activeBots().get(bot.getAsString());
                if (catalogBot == null) {
                    throw JsonContract.invalid(path + " references an inactive or unknown bot: " + bot.getAsString());
                }
                catalogBots.add(catalogBot);
            }
            if (catalogBots.get(0).equals(catalogBots.get(1))) {
                throw JsonContract.invalid(path + " priority pair must contain distinct bots");
            }
            final String pairIdentity = catalogBots.stream().map(CatalogBot::displayName).sorted()
                    .reduce((left, right) -> left + "\n" + right).orElseThrow();
            if (!uniquePairs.add(pairIdentity)) {
                throw JsonContract.invalid(path + " contains a duplicate priority pair");
            }
            final int have = pair.integer("have", 0);
            if (have >= target) {
                throw JsonContract.invalid(path + " priority pair is not under-sampled");
            }
            final String reason = pair.string("reason");
            if (!ADVICE_REASONS.contains(reason)) {
                throw JsonContract.invalid(path + " priority pair has unsupported reason: " + reason);
            }
            pairs.add(new PriorityPair(catalogBots, have, reason));
        }
        return new MatchAdvice(actualGameType, projectionId, target, pairs);
    }

    private static int arrayInteger(final JsonArray array, final int index, final String description,
                                    final int minimum) {
        final JsonElement value = array.get(index);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw JsonContract.invalid(description + " must be an integer");
        }
        try {
            final int result = value.getAsBigDecimal().intValueExact();
            if (result < minimum) {
                throw JsonContract.invalid(description + " must be at least " + minimum);
            }
            return result;
        } catch (ArithmeticException exception) {
            throw JsonContract.invalid(description + " must be an integer", exception);
        }
    }

    private static String matching(final String value, final Pattern pattern, final String message) {
        if (!pattern.matcher(value).matches()) {
            throw JsonContract.invalid(message);
        }
        return value;
    }

    private static boolean sourceBelongsToRepository(final URI source, final URI repository) {
        final String repositoryPath = stripGitSuffix(stripTrailingSlash(repository.getPath()));
        final String sourcePath = source.getPath();
        if (repository.getHost().equalsIgnoreCase("github.com")
                && source.getHost().equalsIgnoreCase("raw.githubusercontent.com")) {
            return sourcePath.startsWith(repositoryPath + "/");
        }
        return repository.getHost().equalsIgnoreCase(source.getHost())
                && sourcePath.startsWith(repositoryPath + "/");
    }

    private static String stripGitSuffix(final String value) {
        return value.endsWith(".git") ? value.substring(0, value.length() - 4) : value;
    }

    private static String stripTrailingSlash(final String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
