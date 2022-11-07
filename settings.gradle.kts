rootProject.name = "radar-gateway"

pluginManagement {
    val kotlinVersion: String by settings
    val ktlintPluginVersion: String by settings
    val dockerComposeVersion: String by settings
    val dependencyUpdatesVersion: String by settings
    plugins {
        kotlin("jvm") version kotlinVersion
        id("com.avast.gradle.docker-compose") version dockerComposeVersion
        id("com.github.ben-manes.versions") version dependencyUpdatesVersion
        id("org.jlleitschuh.gradle.ktlint") version ktlintPluginVersion
    }
}
