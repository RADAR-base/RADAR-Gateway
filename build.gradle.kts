plugins {
    alias(libs.plugins.radar.root.project)
    alias(libs.plugins.radar.dependency.management) apply false
    alias(libs.plugins.radar.kotlin) apply false
}

description = "RADAR Gateway to handle secured data flow to backend."

allprojects {
    repositories {
        mavenCentral()
        maven(url = "https://central.sonatype.com/repository/maven-snapshots/") {
            mavenContent {
                snapshotsOnly()
            }
        }
    }
}

radarRootProject {
    projectVersion.set(libs.versions.project)
    gradleVersion.set(libs.versions.gradle)
}


