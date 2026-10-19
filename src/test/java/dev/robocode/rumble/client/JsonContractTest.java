package dev.robocode.rumble.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("RCL-006")
class JsonContractTest {
    @Test
    void envelopeComparisonDistinguishesAdjacentLargeSelectionSeeds() {
        final String expected = "{\"results\":[{\"selectionSeed\":5888892092719975281}]}";
        final String changed = "{\"results\":[{\"selectionSeed\":5888892092719975282}]}";
        assertFalse(JsonContract.sameValue(JsonParser.parseString(expected), JsonParser.parseString(changed)));
        assertTrue(JsonContract.sameValue(JsonParser.parseString(expected), JsonParser.parseString(expected)));
        assertTrue(JsonContract.sameValue(JsonParser.parseString("{\"b\":2,\"a\":1}"),
                JsonParser.parseString("{\"a\":1,\"b\":2}")));
    }
}
