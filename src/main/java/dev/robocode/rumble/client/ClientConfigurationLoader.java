package dev.robocode.rumble.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Loads and validates the version-one local client configuration.
 */
final class ClientConfigurationLoader {
    private static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final Set<String> SUPPORTED_GAME_TYPES = Set.of("1v1", "twinduel", "melee");
    private static final String EXAMPLE_CLIENT_ID = "replace-with-registered-client-id";

    /**
     * Loads a configuration file and rejects malformed or unsupported settings.
     *
     * @param configurationPath configuration file to load.
     * @return validated local configuration.
     * @throws IOException if the configuration file cannot be read.
     * @throws IllegalArgumentException if the configuration is invalid.
     */
    ClientConfiguration load(final Path configurationPath) throws IOException {
        final JsonObject configuration = parse(configurationPath);
        validateSchemaVersion(configuration);
        validateHttpsUri(configuration, "botsRepo");
        validateHttpsUri(configuration, "dataRepo");
        final String clientId = requiredString(configuration, "clientId");
        if (clientId.equals(EXAMPLE_CLIENT_ID)) {
            throw new IllegalArgumentException("clientId must replace the example value");
        }
        validateStringArray(configuration, "myBots", Set.of(), false);
        validateStringArray(configuration, "gameTypes", SUPPORTED_GAME_TYPES, true);
        validatePositiveInteger(configuration, "battlesPerSession");
        return new ClientConfiguration(clientId, parseMode(requiredString(configuration, "mode")));
    }

    private static JsonObject parse(final Path configurationPath) throws IOException {
        try {
            final JsonElement configuration = JsonParser.parseString(Files.readString(configurationPath));
            if (!configuration.isJsonObject()) {
                throw new IllegalArgumentException("Configuration must be a JSON object");
            }
            return configuration.getAsJsonObject();
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("Configuration must contain valid JSON", exception);
        }
    }

    private static void validateSchemaVersion(final JsonObject configuration) {
        final JsonElement schemaVersion = requiredElement(configuration, "schemaVersion");
        if (integerValue(schemaVersion, "schemaVersion") != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("schemaVersion must be " + SUPPORTED_SCHEMA_VERSION);
        }
    }

    private static void validateHttpsUri(final JsonObject configuration, final String fieldName) {
        final String value = requiredString(configuration, fieldName);
        try {
            final URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException(fieldName + " must be an absolute HTTPS URL");
            }
            if (uri.getRawUserInfo() != null) {
                throw new IllegalArgumentException(fieldName + " must not contain user credentials");
            }
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(fieldName + " must be an absolute HTTPS URL", exception);
        }
    }

    private static void validateStringArray(final JsonObject configuration, final String fieldName,
                                            final Set<String> allowedValues, final boolean required) {
        final JsonElement element = requiredElement(configuration, fieldName);
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException(fieldName + " must be an array of strings");
        }
        final JsonArray values = element.getAsJsonArray();
        if (required && values.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must contain at least one value");
        }
        final Set<String> uniqueValues = new HashSet<>();
        for (final JsonElement value : values) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank()) {
                throw new IllegalArgumentException(fieldName + " must be an array of non-blank strings");
            }
            if (!uniqueValues.add(value.getAsString())) {
                throw new IllegalArgumentException(fieldName + " must not contain duplicate values");
            }
            if (!allowedValues.isEmpty() && !allowedValues.contains(value.getAsString())) {
                throw new IllegalArgumentException(fieldName + " contains unsupported value: " + value.getAsString());
            }
        }
    }

    private static void validatePositiveInteger(final JsonObject configuration, final String fieldName) {
        final JsonElement element = requiredElement(configuration, fieldName);
        if (integerValue(element, fieldName) < 1) {
            throw new IllegalArgumentException(fieldName + " must be a positive integer");
        }
    }

    private static int integerValue(final JsonElement element, final String fieldName) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(fieldName + " must be an integer");
        }
        try {
            return element.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(fieldName + " must be an integer", exception);
        }
    }

    private static String requiredString(final JsonObject configuration, final String fieldName) {
        final JsonElement element = requiredElement(configuration, fieldName);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString() || element.getAsString().isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be a non-blank string");
        }
        return element.getAsString();
    }

    private static JsonElement requiredElement(final JsonObject configuration, final String fieldName) {
        final JsonElement element = configuration.get(fieldName);
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException("Configuration is missing " + fieldName);
        }
        return element;
    }

    private static ClientMode parseMode(final String value) {
        try {
            return ClientMode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("mode must be ranked or practice", exception);
        }
    }
}
