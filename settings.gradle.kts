rootProject.name = "radar-gateway"

include(":deprecated-javax")

pluginManagement {
    val kotlinVersion: String by settings
    plugins {
        kotlin("jvm") version kotlinVersion
    }
}
