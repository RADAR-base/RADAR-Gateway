import org.radarbase.gradle.plugin.radarKotlin
import java.time.Duration

plugins {
    kotlin("plugin.serialization") version Versions.kotlin apply false
    id("org.radarbase.radar-root-project") version Versions.radarCommons
    id("org.radarbase.radar-dependency-management") version Versions.radarCommons
    id("org.radarbase.radar-kotlin") version Versions.radarCommons apply false
    id("com.avast.gradle.docker-compose") version Versions.dockerCompose apply false
}

repositories {
    // Use jcenter for resolving dependencies.
    // You can declare any Maven/Ivy/file repository here.
    mavenCentral()
}

radarRootProject {
    projectVersion.set(Versions.project)
}

subprojects {
    apply(plugin = "org.radarbase.radar-kotlin")

    radarKotlin {
        kotlinVersion.set(Versions.kotlin)
        javaVersion.set(Versions.java)
        log4j2Version.set(Versions.log4j2)
        slf4jVersion.set(Versions.slf4j)
    }
}


