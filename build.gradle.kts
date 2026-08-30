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
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("dev.robocode.tankroyale:robocode-tankroyale-runner:${providers.gradleProperty("tankRoyaleRunnerVersion").get()}")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("dev.robocode.rumble.client.RumbleClient")
}

tasks.test {
    useJUnitPlatform()
    val tankRoyaleSource = providers.gradleProperty("tankRoyaleSource").orElse("../tank-royale").get()
    dependsOn(gradle.includedBuild("tank-royale").task(":sample-bots:java:build"))
    systemProperty("tankRoyaleSampleBotsJava", file(tankRoyaleSource).resolve("sample-bots/java/build/archive"))
}
