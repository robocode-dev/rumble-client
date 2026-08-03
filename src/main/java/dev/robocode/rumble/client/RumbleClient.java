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
    private static final String SYNCHRONIZE_OPTION = "--sync";
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

        if (arguments.length > 2
                || (!arguments[0].equals(VALIDATE_CONFIG_OPTION) && !arguments[0].equals(SYNCHRONIZE_OPTION))) {
            throw new IllegalArgumentException("Expected --validate-config [path], --sync [path], or --help");
        }

        final Path configurationPath = arguments.length == 2 ? Path.of(arguments[1]) : DEFAULT_CONFIGURATION_PATH;
        final ClientConfiguration configuration = new ClientConfigurationLoader().load(configurationPath);
        if (arguments[0].equals(SYNCHRONIZE_OPTION)) {
            final RumbleSnapshot snapshot = new RumbleSynchronizer(new GitRepositoryReader())
                    .synchronize(configuration);
            output.printf("Synchronized %s at %s.%n", snapshot.canonicalDataRepository(), snapshot.dataRevision());
            output.printf("Accepted behavior version %d, %d active bots, and advice for %d game types.%n",
                    snapshot.engine().behaviorVersion(), snapshot.catalog().activeBots().size(), snapshot.advice().size());
            return;
        }
        output.printf("Configuration %s is valid for %s mode.%n", configurationPath, configuration.mode().displayName());
        output.println("Battle execution is not available yet.");
    }

    private static boolean hasOnlyArgument(final String[] arguments, final String option) {
        return arguments.length == 1 && arguments[0].equals(option);
    }

    private static void printHelp(final PrintStream output) {
        output.println("Tank Royale Rumble Client");
        output.println("Usage: rumble-client --validate-config [path]");
        output.println("       rumble-client --sync [path]");
        output.println("       rumble-client --help");
        output.println();
        output.println("Use --validate-config to check a local ranked or practice configuration.");
        output.println("Use --sync to validate the current canonical ranked input snapshot.");
    }
}
