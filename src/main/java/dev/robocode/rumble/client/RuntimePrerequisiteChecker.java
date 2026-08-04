package dev.robocode.rumble.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifies the host runtimes needed to execute every supported bot platform.
 */
final class RuntimePrerequisiteChecker {
    private static final String VERSION_RESOURCE = "/runtime-versions.properties";
    private static final Pattern VERSION = Pattern.compile("(?<![0-9])([0-9]+)(?:\\.([0-9]+))?");

    private final CommandRunner commandRunner;

    RuntimePrerequisiteChecker() {
        this(RuntimePrerequisiteChecker::runCommand);
    }

    RuntimePrerequisiteChecker(final CommandRunner commandRunner) {
        this.commandRunner = commandRunner;
    }

    RuntimeReport check() throws IOException {
        final Map<String, RequiredVersion> required = loadRequiredVersions();
        final List<RuntimeStatus> statuses = new ArrayList<>();
        statuses.add(checkRuntime("Java", List.of(List.of("java", "-version")), required.get("java")));
        statuses.add(checkRuntime(".NET SDK", List.of(List.of("dotnet", "--version")), required.get("dotnet")));
        statuses.add(checkRuntime("Python", List.of(List.of("python3.12", "--version"),
                List.of("py", "-3.12", "--version"), List.of("python3", "--version"),
                List.of("python", "--version")), required.get("python")));
        statuses.add(checkRuntime("Node.js", List.of(List.of("node", "--version")), required.get("node")));
        return new RuntimeReport(statuses);
    }

    private RuntimeStatus checkRuntime(final String name, final List<List<String>> commands,
                                       final RequiredVersion required) {
        IOException lastFailure = null;
        for (final List<String> command : commands) {
            try {
                final CommandResult result = commandRunner.run(command);
                if (result.exitCode() != 0) {
                    lastFailure = new IOException(String.join(" ", command) + " exited with " + result.exitCode());
                    continue;
                }
                final RequiredVersion actual = parseVersion(result.output(), name);
                if (!actual.satisfies(required)) {
                    return RuntimeStatus.failure(name, required, "found " + actual.display());
                }
                return RuntimeStatus.success(name, required, actual.display());
            } catch (IOException exception) {
                lastFailure = exception;
            }
        }
        final String detail = lastFailure == null ? "command unavailable" : lastFailure.getMessage();
        return RuntimeStatus.failure(name, required, detail);
    }

    private static RequiredVersion parseVersion(final String output, final String name) throws IOException {
        final Matcher matcher = VERSION.matcher(output);
        if (!matcher.find()) {
            throw new IOException(name + " did not report a recognizable version");
        }
        final int major = Integer.parseInt(matcher.group(1));
        final int minor = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        return new RequiredVersion(major, minor, matcher.group(2) != null);
    }

    private static Map<String, RequiredVersion> loadRequiredVersions() throws IOException {
        final Properties properties = new Properties();
        try (InputStream input = RuntimePrerequisiteChecker.class.getResourceAsStream(VERSION_RESOURCE)) {
            if (input == null) {
                throw new IOException("Missing " + VERSION_RESOURCE);
            }
            properties.load(input);
        }
        final Map<String, RequiredVersion> versions = new LinkedHashMap<>();
        for (final String runtime : List.of("java", "dotnet", "python", "node")) {
            versions.put(runtime, RequiredVersion.parse(properties.getProperty(runtime), runtime));
        }
        return versions;
    }

    private static CommandResult runCommand(final List<String> command) throws IOException {
        final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        final String output;
        try (InputStream input = process.getInputStream()) {
            output = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        try {
            return new CommandResult(process.waitFor(), output.trim());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while checking " + command.get(0), exception);
        }
    }

    @FunctionalInterface
    interface CommandRunner {
        CommandResult run(List<String> command) throws IOException;
    }
}

record CommandResult(int exitCode, String output) {
}

record RequiredVersion(int major, int minor, boolean checksMinor) {
    static RequiredVersion parse(final String value, final String runtime) throws IOException {
        if (value == null || !value.matches("[0-9]+(?:\\.[0-9]+)?")) {
            throw new IOException("Invalid required " + runtime + " version: " + value);
        }
        final String[] parts = value.split("\\.");
        return new RequiredVersion(Integer.parseInt(parts[0]), parts.length == 2 ? Integer.parseInt(parts[1]) : 0,
                parts.length == 2);
    }

    boolean satisfies(final RequiredVersion required) {
        return major == required.major && (!required.checksMinor || minor == required.minor);
    }

    String display() {
        return checksMinor ? major + "." + minor : Integer.toString(major);
    }
}

record RuntimeStatus(String name, RequiredVersion required, boolean available, String detail) {
    static RuntimeStatus success(final String name, final RequiredVersion required, final String actual) {
        return new RuntimeStatus(name, required, true, actual);
    }

    static RuntimeStatus failure(final String name, final RequiredVersion required, final String detail) {
        return new RuntimeStatus(name, required, false, detail);
    }
}

record RuntimeReport(List<RuntimeStatus> statuses) {
    RuntimeReport {
        statuses = List.copyOf(statuses);
    }

    boolean ready() {
        return statuses.stream().allMatch(RuntimeStatus::available);
    }
}
