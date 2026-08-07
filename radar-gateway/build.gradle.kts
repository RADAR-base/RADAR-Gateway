import com.github.jk1.license.filter.ExcludeTransitiveDependenciesFilter
import java.time.Duration

plugins {
    application
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.radar.kotlin)
    alias(libs.plugins.docker.compose)
}

description = "RADAR Gateway to handle secured data flow to backend."

application {
    mainClass.set("org.radarbase.gateway.MainKt")

    applicationDefaultJvmArgs = listOf(
        "-Dcom.sun.management.jmxremote",
        "-Dcom.sun.management.jmxremote.local.only=false",
        "-Dcom.sun.management.jmxremote.port=9010",
        "-Dcom.sun.management.jmxremote.authenticate=false",
        "-Dcom.sun.management.jmxremote.ssl=false",
    )
}

val integrationTestSourceSet = sourceSets.create("integrationTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

val integrationTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}

licenseReport {
    filters = arrayOf(ExcludeTransitiveDependenciesFilter())
    allowedLicensesFile = File("all-licenses.json")
}

val integrationTest by tasks.registering(Test::class) {
    description = "Runs integration tests."
    group = "verification"
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    testLogging.showStandardStreams = true
    shouldRunAfter("test")
    outputs.upToDateWhen { false }
}

dockerCompose {
    useComposeFiles.set(listOf("src/integrationTest/docker/docker-compose.yml"))
    val dockerComposeBuild: String? by project
    val doBuild = dockerComposeBuild?.toBoolean() ?: true
    buildBeforeUp.set(doBuild)
    buildBeforePull.set(doBuild)
    buildAdditionalArgs.set(emptyList<String>())
    val dockerComposeStopContainers: String? by project
    stopContainers.set(dockerComposeStopContainers?.toBoolean() ?: true)
    waitForTcpPortsTimeout.set(Duration.ofMinutes(3))
    environment.put("SERVICES_HOST", "localhost")
    captureContainersOutputToFiles.set(project.file("build/container-logs"))
    isRequiredBy(integrationTest)
}

configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())

// --- Vulnerability fixes start ---
configurations.all {
    resolutionStrategy.dependencySubstitution {
        // Substitute the old group/module with drop-in replacement
        substitute(module("org.lz4:lz4-java"))
            .using(module(rootProject.libs.lz4.get().toString()))
            .because("Force safe version of LZ4 across all modules")
    }
}
// --- Vulnerability fixes end ---

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))

    implementation(libs.jersey.media.multipart)

    implementation(libs.radar.commons)
    implementation(libs.radar.commons.kotlin)
    implementation(libs.radar.jersey)
    implementation(libs.managementportal.client)
    implementation(libs.lzfse.decode)
    implementation(libs.radar.auth)
    implementation(libs.minio)

    implementation(libs.kafka.clients)
    implementation(libs.kafka.avro.serializer)
    implementation(libs.kafka.schema.registry.client)

    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)

    implementation(platform(libs.ktor.bom))
    implementation(libs.ktor.client.auth)

    runtimeOnly(libs.avro)

    runtimeOnly(libs.grizzly.framework.monitoring)
    runtimeOnly(libs.grizzly.http.monitoring)
    runtimeOnly(libs.grizzly.http.server.monitoring)

    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.hamcrest)
    testImplementation(libs.mockwebserver)

    integrationTestImplementation(platform(libs.ktor.bom))
    integrationTestImplementation(libs.ktor.client.content.negotiation)
    integrationTestImplementation(libs.ktor.serialization.kotlinx.json)

    testImplementation(libs.radar.schemas.commons)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockk)
    integrationTestImplementation(libs.radar.schemas.commons)
    integrationTestImplementation(libs.radar.commons.testing)
}

radarKotlin {
    log4j2Version.set(libs.versions.log4j2)
    sentryEnabled.set(true)
    openTelemetryAgentEnabled.set(false)
}
