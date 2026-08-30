package dev.robocode.rumble.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Fail-fast access to one versioned JSON contract document.
 */
final class JsonContract {
    private static final int SUPPORTED_SCHEMA_VERSION = 1;

    private final JsonObject object;
    private final String document;

    private JsonContract(final JsonObject object, final String document) {
        this.object = object;
        this.document = document;
    }

    static JsonContract parse(final String json, final String document) {
        try {
            final JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                throw invalid(document + " must contain a JSON object");
            }
            final JsonContract contract = new JsonContract(root.getAsJsonObject(), document);
            if (contract.integer("schemaVersion", 1) != SUPPORTED_SCHEMA_VERSION) {
                throw invalid(document + " has an unsupported schemaVersion");
            }
            return contract;
        } catch (JsonParseException exception) {
            throw invalid(document + " contains invalid JSON", exception);
        }
    }

    String string(final String field) {
        final JsonElement value = required(field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank()) {
            throw invalid(document + "." + field + " must be a non-blank string");
        }
        return value.getAsString();
    }

    String nullableString(final String field) {
        final JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        return string(field);
    }

    int integer(final String field, final int minimum) {
        final JsonElement value = required(field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw invalid(document + "." + field + " must be an integer");
        }
        try {
            final int result = value.getAsBigDecimal().intValueExact();
            if (result < minimum) {
                throw invalid(document + "." + field + " must be at least " + minimum);
            }
            return result;
        } catch (ArithmeticException exception) {
            throw invalid(document + "." + field + " must be an integer", exception);
        }
    }

    URI httpsUri(final String field) {
        final String value = string(field);
        try {
            final URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw invalid(document + "." + field + " must be an absolute credential-free HTTPS URL");
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw invalid(document + "." + field + " must be an absolute credential-free HTTPS URL", exception);
        }
    }

    JsonArray array(final String field) {
        final JsonElement value = required(field);
        if (!value.isJsonArray()) {
            throw invalid(document + "." + field + " must be an array");
        }
        return value.getAsJsonArray();
    }

    JsonArray optionalArray(final String field) {
        final JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            return new JsonArray();
        }
        if (!value.isJsonArray()) {
            throw invalid(document + "." + field + " must be an array");
        }
        return value.getAsJsonArray();
    }

    JsonObject object(final String field) {
        final JsonElement value = required(field);
        if (!value.isJsonObject()) {
            throw invalid(document + "." + field + " must be an object");
        }
        return value.getAsJsonObject();
    }

    static JsonContract nested(final JsonObject object, final String document) {
        return new JsonContract(object, document);
    }

    private JsonElement required(final String field) {
        final JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            throw invalid(document + " is missing " + field);
        }
        return value;
    }

    static IllegalArgumentException invalid(final String message) {
        return new IllegalArgumentException(message);
    }

    static IllegalArgumentException invalid(final String message, final Exception cause) {
        return new IllegalArgumentException(message, cause);
    }
}
