package dev.robocode.rumble.client;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;

/**
 * Command-line entry point for Tank Royale Rumble battle contribution.
 */
public final class RumbleClient {
    private static final String HELP_OPTION = "--help";
    private static final String VALIDATE_CONFIG_OPTION = "--validate-config";
    private static final Path DEFAULT_CONFIGURATION_PATH = Path.of("rumble-client.json");

    private RumbleClient() {
    }

    /**
     * Starts the Rumble client command-line interface.
     *
     * @param arguments command-line arguments.
     */
    public static void main(final String[] arguments) {
        try {
            run(arguments, System.out);
        } catch (IllegalArgumentException | IOException exception) {
            System.err.println("Error: " + exception.getMessage());
            System.err.println("Run with --help for usage.");
            System.exit(1);
        }
    }

    static void run(final String[] arguments, final PrintStream output) throws IOException {
        if (arguments.length == 0 || hasOnlyArgument(arguments, HELP_OPTION)) {
            printHelp(output);
            return;
        }

        if (!arguments[0].equals(VALIDATE_CONFIG_OPTION) || arguments.length > 2) {
            throw new IllegalArgumentException("Expected --validate-config [path] or --help");
        }

        final Path configurationPath = arguments.length == 2 ? Path.of(arguments[1]) : DEFAULT_CONFIGURATION_PATH;
        final ClientConfiguration configuration = new ClientConfigurationLoader().load(configurationPath);
        output.printf("Configuration %s is valid for %s mode.%n", configurationPath, configuration.mode().displayName());
        output.println("Ranked synchronization and battle execution are not available yet.");
    }

    private static boolean hasOnlyArgument(final String[] arguments, final String option) {
        return arguments.length == 1 && arguments[0].equals(option);
    }

    private static void printHelp(final PrintStream output) {
        output.println("Tank Royale Rumble Client");
        output.println("Usage: rumble-client --validate-config [path]");
        output.println("       rumble-client --help");
        output.println();
        output.println("Use --validate-config to check a local ranked or practice configuration.");
    }
}
