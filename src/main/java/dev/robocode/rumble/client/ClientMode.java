package dev.robocode.rumble.client;

/**
 * Separates ranked contribution from local-only practice execution.
 */
public enum ClientMode {
    /**
     * Executes only contract-validated battles that are eligible for submission.
     */
    RANKED,

    /**
     * Executes local battles and never produces a ranked journal record.
     */
    PRACTICE;

    /**
     * Reports whether this mode may append a ranked result journal.
     *
     * @return {@code true} only for ranked execution.
     */
    public boolean permitsRankedJournal() {
        return this == RANKED;
    }

    /**
     * Returns the lowercase name accepted by the configuration file.
     *
     * @return configuration-facing mode name.
     */
    public String displayName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
