import org.radarbase.gradle.plugin.radarKotlin
import java.time.Duration

plugins {
    kotlin("plugin.serialization") version Versions.kotlin
    id("application")
    id("org.radarbase.radar-root-project") version Versions.radarCommons
    id("org.radarbase.radar-dependency-management") version Versions.radarCommons
    id("org.radarbase.radar-kotlin") version Versions.radarCommons
    id("com.avast.gradle.docker-compose") version Versions.dockerCompose
}

description = "RADAR Gateway to handle secured data flow to backend."

radarRootProject {
    projectVersion.set(Versions.project)
}

radarKotlin {
    kotlinVersion.set(Versions.kotlin)
    javaVersion.set(Versions.java)
    log4j2Version.set(Versions.log4j2)
    slf4jVersion.set(Versions.slf4j)
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

    implementation("org.radarbase:radar-commons:${Versions.radarCommons}")
    implementation("org.radarbase:radar-commons-kotlin:${Versions.radarCommons}")
    implementation("org.radarbase:radar-jersey:${Versions.radarJersey}")
    implementation("org.radarbase:managementportal-client:${Versions.radarAuth}")
    implementation("org.radarbase:lzfse-decode:${Versions.lzfse}")
    implementation("org.radarbase:radar-auth:${Versions.radarAuth}")

    implementation("org.apache.kafka:kafka-clients:${Versions.kafka}")
    implementation("io.confluent:kafka-avro-serializer:${Versions.confluent}")
    implementation("io.confluent:kafka-schema-registry-client:${Versions.confluent}")

    implementation(platform("com.fasterxml.jackson:jackson-bom:${Versions.jackson}"))
    implementation("com.fasterxml.jackson.core:jackson-databind")

    implementation(platform("io.ktor:ktor-bom:${Versions.ktor}"))
    implementation("io.ktor:ktor-client-auth")

    runtimeOnly("org.apache.avro:avro:${Versions.avro}")

    runtimeOnly("org.glassfish.grizzly:grizzly-framework-monitoring:${Versions.grizzly}")
    runtimeOnly("org.glassfish.grizzly:grizzly-http-monitoring:${Versions.grizzly}")
    runtimeOnly("org.glassfish.grizzly:grizzly-http-server-monitoring:${Versions.grizzly}")

    testImplementation("org.junit.jupiter:junit-jupiter-api:${Versions.junit}")
    testImplementation("org.mockito.kotlin:mockito-kotlin:${Versions.mockitoKotlin}")
    testImplementation("org.hamcrest:hamcrest:${Versions.hamcrest}")

    integrationTestImplementation(platform("io.ktor:ktor-bom:${Versions.ktor}"))
    integrationTestImplementation("io.ktor:ktor-client-content-negotiation")
    integrationTestImplementation("io.ktor:ktor-client-content-negotiation")
    integrationTestImplementation("io.ktor:ktor-serialization-kotlinx-json")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${Versions.junit}")

    testImplementation("org.radarbase:radar-schemas-commons:${Versions.radarSchemas}")
    integrationTestImplementation("org.radarbase:radar-schemas-commons:${Versions.radarSchemas}")
    integrationTestImplementation("org.radarbase:radar-commons-testing:${Versions.radarCommons}")
}

val integrationTest by tasks.registering(Test::class) {
    description = "Runs integration tests."
    group = "verification"
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    shouldRunAfter("test")
    outputs.upToDateWhen { false }
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
        "-Dcom.sun.management.jmxremote.ssl=false",
    )
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

tasks.register("listrepos") {
    doLast {
        println("Repositories:")
        project.repositories.map{it as MavenArtifactRepository}
            .forEach{
                println("Name: ${it.name}; url: ${it.url}")
            }
    }
}
