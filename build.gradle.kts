plugins {
    kotlin("plugin.serialization") version Versions.kotlin apply false
    id("org.radarbase.radar-root-project") version Versions.radarCommons
    id("org.radarbase.radar-dependency-management") version Versions.radarCommons
    id("org.radarbase.radar-kotlin") version Versions.radarCommons apply false
    id("com.avast.gradle.docker-compose") version Versions.dockerCompose apply false
}

description = "RADAR Gateway to handle secured data flow to backend."

radarRootProject {
    projectVersion.set(Versions.project)
}


