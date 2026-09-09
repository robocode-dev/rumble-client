import dev.robocode.tankroyale.runner.BattleRunner;
import dev.robocode.tankroyale.runner.BattleSetup;
import dev.robocode.tankroyale.runner.BotEntry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

var firstBot = Path.of(System.getenv().getOrDefault("SMOKE_BOT_ONE", "/work/bots/one/Target"));
var secondBot = Path.of(System.getenv().getOrDefault("SMOKE_BOT_TWO", "/work/bots/two/Walls"));
if (!Files.isDirectory(firstBot) || !Files.isDirectory(secondBot)) {
    throw new IllegalStateException("Smoke bot archives are not mounted");
}

var setup = BattleSetup.oneVsOne(builder -> {
    builder.setNumberOfRounds(1);
    builder.setArenaWidth(800);
    builder.setArenaHeight(600);
    builder.setReadyTimeoutMicros(10_000_000);
});

try (var runner = BattleRunner.create(builder -> builder.embeddedServer().requireBehaviorVersion(1))) {
    var results = runner.runBattle(setup, List.of(BotEntry.of(firstBot), BotEntry.of(secondBot)));
    var output = results.toString();
    if (!output.contains("BattleResults(numberOfRounds=1,")) {
        throw new IllegalStateException("Battle did not report one round: " + output);
    }
    Files.writeString(
        Path.of(System.getenv().getOrDefault("SMOKE_SUCCESS_FILE", "/tmp/rumble-smoke-success")),
        "ok"
    );
    System.out.println(output);
} catch (Throwable error) {
    throw new RuntimeException("Container smoke battle failed", error);
}
