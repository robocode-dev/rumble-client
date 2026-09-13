plugins {
    application
    java
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("dev.robocode.tankroyale:robocode-tankroyale-runner:${providers.gradleProperty("tankRoyaleRunnerVersion").get()}")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("dev.robocode.rumble.client.RumbleClient")
}

tasks.test {
    useJUnitPlatform()
    val tankRoyaleSource = providers.gradleProperty("tankRoyaleSource").orNull
    if (tankRoyaleSource == null) {
        // The real-runner integration test needs sample bots from a source checkout.
        // Keep the published Maven dependency usable for ordinary local builds.
        exclude("**/RunnerBattleExecutorIntegrationTest.class")
    } else {
        dependsOn(gradle.includedBuild("tank-royale").task(":sample-bots:java:build"))
        systemProperty("tankRoyaleSampleBotsJava", file(tankRoyaleSource).resolve("sample-bots/java/build/archive"))
    }
}
