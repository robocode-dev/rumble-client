package dev.robocode.rumble.client;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimePrerequisiteCheckerTest {
    @Test
    @Tag("Unit")
    void testUnitPositive_acceptsEveryPinnedRuntimeLane() throws IOException {
        final Map<String, String> versions = Map.of(
                "java", "openjdk version \"25.0.1\"",
                "dotnet", "10.0.112",
                "python3.14", "Python 3.14.7",
                "node", "v24.21.0");

        final RuntimeReport report = new RuntimePrerequisiteChecker(command ->
                new CommandResult(0, versions.get(command.get(0)))).check();

        assertTrue(report.ready());
    }

    @Test
    @Tag("Unit")
    void testUnitNegative_reportsMissingOrMismatchedRuntimesWithoutInstallingThem() throws IOException {
        final List<List<String>> invoked = new java.util.ArrayList<>();
        final RuntimeReport report = new RuntimePrerequisiteChecker(command -> {
            invoked.add(List.copyOf(command));
            return switch (command.get(0)) {
                case "java" -> new CommandResult(0, "openjdk version \"26.0.1\"");
                case "dotnet" -> throw new IOException("command unavailable");
                case "python3.14" -> new CommandResult(0, "Python 3.14.7");
                case "node" -> new CommandResult(0, "v24.21.0");
                default -> throw new IOException("command unavailable");
            };
        }).check();

        assertFalse(report.ready());
        assertTrue(report.statuses().stream().anyMatch(status -> status.name().equals("Java") && !status.available()));
        assertTrue(report.statuses().stream().anyMatch(status -> status.name().equals(".NET SDK") && !status.available()));
        assertFalse(invoked.stream().flatMap(List::stream).anyMatch(argument -> argument.equals("install")));
    }
}
