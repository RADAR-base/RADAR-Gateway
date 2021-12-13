import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.jetbrains.kotlin.cli.common.toBooleanLenient
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.Duration

plugins {
    id("idea")
    id("application")
    kotlin("jvm")
    id("com.avast.gradle.docker-compose") version "0.14.11"
    id("com.github.ben-manes.versions") version "0.39.0"
}

description = "RADAR Gateway to handle secured data flow to backend."

allprojects {
    group = "org.radarbase"
    version = "0.5.8"

    repositories {
        mavenCentral()
        maven(url = "https://packages.confluent.io/maven/")
    }
}

val integrationTestSourceSet = sourceSets.create("integrationTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

val integrationTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}

configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))

    val radarCommonsVersion: String by project
    implementation("org.radarbase:radar-commons:$radarCommonsVersion")
    val radarJerseyVersion: String by project
    implementation("org.radarbase:radar-jersey:$radarJerseyVersion")
    val radarAuthVersion: String by project
    implementation("org.radarbase:managementportal-client:$radarAuthVersion")
    val lzfseVersion: String by project
    implementation("org.radarbase:lzfse-decode:$lzfseVersion")

    val kafkaVersion: String by project
    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")
    val confluentVersion: String by project
    implementation("io.confluent:kafka-avro-serializer:$confluentVersion")
    implementation("io.confluent:kafka-schema-registry-client:$confluentVersion")

    val slf4jVersion: String by project
    implementation("org.slf4j:slf4j-api:$slf4jVersion")

    val jacksonVersion: String by project
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")

    val avroVersion: String by project
    runtimeOnly("org.apache.avro:avro:$avroVersion")

    val grizzlyVersion: String by project
    runtimeOnly("org.glassfish.grizzly:grizzly-framework-monitoring:$grizzlyVersion")
    runtimeOnly("org.glassfish.grizzly:grizzly-http-monitoring:$grizzlyVersion")
    runtimeOnly("org.glassfish.grizzly:grizzly-http-server-monitoring:$grizzlyVersion")

    val log4j2Version: String by project
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j-impl:$log4j2Version")
    runtimeOnly("org.apache.logging.log4j:log4j-api:$log4j2Version")
    runtimeOnly("org.apache.logging.log4j:log4j-jul:$log4j2Version")

    val junitVersion: String by project
    val okhttp3Version: String by project
    val radarSchemasVersion: String by project
    val mockitoKotlinVersion: String by project
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.mockito.kotlin:mockito-kotlin:$mockitoKotlinVersion")
    testImplementation("com.squareup.okhttp3:mockwebserver:$okhttp3Version")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

    testImplementation("org.radarbase:radar-schemas-commons:$radarSchemasVersion")
    integrationTestImplementation("com.squareup.okhttp3:okhttp:$okhttp3Version")
    integrationTestImplementation("org.radarbase:radar-schemas-commons:$radarSchemasVersion")
    integrationTestImplementation("org.radarbase:radar-commons-testing:$radarCommonsVersion")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
        apiVersion = "1.6"
        languageVersion = "1.6"
    }
}

val integrationTest by tasks.registering(Test::class) {
    description = "Runs integration tests."
    group = "verification"
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    shouldRunAfter("test")
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
    useComposeFiles.set(listOf("src/integrationTest/docker/docker-compose.yml"))
    val dockerComposeBuild: String? by project
    val doBuild = dockerComposeBuild?.toBooleanLenient() ?: true
    buildBeforeUp.set(doBuild)
    buildBeforePull.set(doBuild)
    buildAdditionalArgs.set(emptyList<String>())
    val dockerComposeStopContainers: String? by project
    stopContainers.set(dockerComposeStopContainers?.toBooleanLenient() ?: true)
    waitForTcpPortsTimeout.set(Duration.ofMinutes(3))
    environment.put("SERVICES_HOST", "localhost")
    captureContainersOutputToFiles.set(project.file("build/container-logs"))
    isRequiredBy(integrationTest)
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

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.toUpperCase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}

tasks.withType<DependencyUpdatesTask> {
    rejectVersionIf {
        isNonStable(candidate.version)
    }
}

tasks.wrapper {
    gradleVersion = "7.3.1"
}
