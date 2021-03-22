import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme
import org.gradle.kotlin.dsl.support.serviceOf
import org.jetbrains.kotlin.cli.common.toBooleanLenient
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.Duration

plugins {
    id("idea")
    id("application")
    kotlin("jvm")
    id("org.unbroken-dome.test-sets") version "3.0.1"
    id("com.avast.gradle.docker-compose") version "0.14.1"
}

group = "org.radarbase"
version = "0.5.4"
description = "RADAR Gateway to handle secured data flow to backend."

allprojects {
    dependencyLocking {
        lockAllConfigurations()
    }

    configurations {
        // Avoid non-release versions from wildcards
        all {
            val versionSelectorScheme = serviceOf<VersionSelectorScheme>()
            resolutionStrategy.componentSelection.all {
                if (candidate.version.contains("-SNAPSHOT")
                    || candidate.version.contains("-M[0-9]+".toRegex())
                    || candidate.version.contains("-rc", ignoreCase = true)
                    || candidate.version.contains(".Draft", ignoreCase = true)
                    || candidate.version.contains("-alpha", ignoreCase = true)
                    || candidate.version.contains("-beta", ignoreCase = true)
                ) {
                    val dependency =
                        allDependencies.find { it.group == candidate.group && it.name == candidate.module }
                    if (dependency != null && !versionSelectorScheme.parseSelector(dependency.version)
                            .matchesUniqueVersion()
                    ) {
                        reject("only releases are allowed for $dependency")
                    }
                }
            }
        }
    }
}

repositories {
    mavenCentral()
    // Non-jcenter radar releases
    maven(url = "https://dl.bintray.com/radar-cns/org.radarcns")
    maven(url = "https://dl.bintray.com/radar-base/org.radarbase")
    maven(url = "https://packages.confluent.io/maven/")
    jcenter()
}

val integrationTest = testSets.create("integrationTest")

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))

    val radarCommonsVersion: String by project
    implementation("org.radarbase:radar-commons:$radarCommonsVersion")
    implementation("org.radarbase:radar-jersey:${project.property("radarJerseyVersion")}")
    implementation("org.radarbase:managementportal-client:${project.property("radarAuthVersion")}")
    implementation("org.radarbase:lzfse-decode:${project.property("lzfseVersion")}")

    implementation(project(path = ":deprecated-javax", configuration = "shadow"))

    implementation("org.slf4j:slf4j-api:${project.property("slf4jVersion")}")
    implementation("com.fasterxml.jackson.core:jackson-databind:${project.property("jacksonVersion")}")

    val grizzlyVersion: String by project
    runtimeOnly("org.glassfish.grizzly:grizzly-framework-monitoring:$grizzlyVersion")
    runtimeOnly("org.glassfish.grizzly:grizzly-http-monitoring:$grizzlyVersion")
    runtimeOnly("org.glassfish.grizzly:grizzly-http-server-monitoring:$grizzlyVersion")
    runtimeOnly("ch.qos.logback:logback-classic:${project.property("logbackVersion")}")

    val junitVersion: String by project
    val okhttp3Version: String by project
    val radarSchemasVersion: String by project
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:[2.2,3.0)")
    testImplementation("com.squareup.okhttp3:mockwebserver:$okhttp3Version")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

    testImplementation("org.radarcns:radar-schemas-commons:$radarSchemasVersion")
    integrationTest.implementationConfigurationName("com.squareup.okhttp3:okhttp:$okhttp3Version")
    integrationTest.implementationConfigurationName("org.radarcns:radar-schemas-commons:$radarSchemasVersion")
    integrationTest.implementationConfigurationName("org.radarbase:radar-commons-testing:$radarCommonsVersion")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
        apiVersion = "1.4"
        languageVersion = "1.4"
    }
}

tasks.withType<Test> {
    testLogging {
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    useJUnitPlatform()
}

tasks.withType<Tar> {
    compression = Compression.GZIP
    archiveExtension.set("tar.gz")
}

application {
    mainClass.set("org.radarbase.gateway.MainKt")

    applicationDefaultJvmArgs = listOf(
        "-Dcom.sun.management.jmxremote",
        "-Dcom.sun.management.jmxremote.local.only=false",
        "-Dcom.sun.management.jmxremote.port=9010",
        "-Dcom.sun.management.jmxremote.authenticate=false",
        "-Dcom.sun.management.jmxremote.ssl=false"
    )
}

dockerCompose {
    useComposeFiles = listOf("src/integrationTest/docker/docker-compose.yml")
    buildAdditionalArgs = emptyList<String>()
    val dockerComposeStopContainers: String? by project
    stopContainers = dockerComposeStopContainers?.toBooleanLenient() ?: true
    waitForTcpPortsTimeout = Duration.ofMinutes(3)
    environment["SERVICES_HOST"] = "localhost"
    isRequiredBy(tasks["integrationTest"])
}

idea {
    module {
        isDownloadSources = true
    }
}


allprojects {
    tasks.register("downloadDockerDependencies") {
        doFirst {
            configurations["compileClasspath"].files
            configurations["runtimeClasspath"].files
            println("Downloaded all dependencies")
        }
        outputs.upToDateWhen { false }
    }

    tasks.register("downloadDependencies") {
        doFirst {
            configurations.asMap
                .filterValues { it.isCanBeResolved }
                .forEach { (name, config) ->
                    try {
                        config.files
                    } catch (ex: Exception) {
                        project.logger.warn("Cannot find dependency for configuration {}", name, ex)
                    }
                }
            println("Downloaded all dependencies")
        }
        outputs.upToDateWhen { false }
    }
}

tasks.wrapper {
    gradleVersion = "6.8.3"
}
