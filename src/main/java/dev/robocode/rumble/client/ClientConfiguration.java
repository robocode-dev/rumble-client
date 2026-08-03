package dev.robocode.rumble.client;

/**
 * Validated local settings that determine how the client may run.
 *
 * @param clientId registered client identity.
 * @param mode local execution mode.
 */
record ClientConfiguration(String clientId, ClientMode mode) {
}
