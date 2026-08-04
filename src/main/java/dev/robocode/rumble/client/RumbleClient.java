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
    private static final String CHECK_RUNTIMES_OPTION = "--check-runtimes";
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
        run(arguments, output, new RuntimePrerequisiteChecker()::check);
    }

    static void run(final String[] arguments, final PrintStream output, final RuntimeCheck runtimeCheck)
            throws IOException {
        if (arguments.length == 0 || hasOnlyArgument(arguments, HELP_OPTION)) {
            printHelp(output);
            return;
        }

        if (hasOnlyArgument(arguments, CHECK_RUNTIMES_OPTION)) {
            printRuntimeReport(runtimeCheck.check(), output);
            return;
        }

        if (arguments.length > 2
                || (!arguments[0].equals(VALIDATE_CONFIG_OPTION) && !arguments[0].equals(SYNCHRONIZE_OPTION))) {
            throw new IllegalArgumentException(
                    "Expected --validate-config [path], --check-runtimes, --sync [path], or --help");
        }

        final Path configurationPath = arguments.length == 2 ? Path.of(arguments[1]) : DEFAULT_CONFIGURATION_PATH;
        final ClientConfiguration configuration = new ClientConfigurationLoader().load(configurationPath);
        if (arguments[0].equals(SYNCHRONIZE_OPTION)) {
            final GitRepositoryReader repositoryReader = new GitRepositoryReader();
            final RumbleSnapshot snapshot = new RumbleSynchronizer(repositoryReader).synchronize(configuration);
            final PreparedBotCache botCache = new BotCachePreparer(repositoryReader).prepare(snapshot, configuration);
            output.printf("Synchronized %s at %s.%n", snapshot.canonicalDataRepository(), snapshot.dataRevision());
            output.printf("Accepted behavior version %d, cached %d active bots at %s, and advice for %d game types.%n",
                    snapshot.engine().behaviorVersion(), botCache.bots().size(), botCache.sourceCommit(),
                    snapshot.advice().size());
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
        output.println("       rumble-client --check-runtimes");
        output.println("       rumble-client --sync [path]");
        output.println("       rumble-client --help");
        output.println();
        output.println("Use --validate-config to check a local ranked or practice configuration.");
        output.println("Use --check-runtimes to verify native Java, .NET, Python, and Node.js prerequisites.");
        output.println("Use --sync to validate the current ranked snapshot and prepare its immutable bot cache.");
    }

    private static void printRuntimeReport(final RuntimeReport report, final PrintStream output) {
        for (final RuntimeStatus status : report.statuses()) {
            output.printf("%s %s (required %s): %s%n", status.available() ? "OK" : "MISSING",
                    status.name(), status.required().display(), status.detail());
        }
        if (!report.ready()) {
            throw new IllegalArgumentException(
                    "Install the missing native prerequisites or use the recommended Docker distribution");
        }
    }

    @FunctionalInterface
    interface RuntimeCheck {
        RuntimeReport check() throws IOException;
    }
}
