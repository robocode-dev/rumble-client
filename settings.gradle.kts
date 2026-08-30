rootProject.name = "rumble-client"

providers.gradleProperty("tankRoyaleSource").orNull?.let { sourcePath ->
    includeBuild(file(sourcePath)) {
        name = "tank-royale"
        dependencySubstitution {
            substitute(module("dev.robocode.tankroyale:robocode-tankroyale-runner"))
                .using(project(":runner"))
        }
    }
}
