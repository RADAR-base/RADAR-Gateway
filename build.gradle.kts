import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.Duration

plugins {
    id("idea")
    id("application")
    kotlin("jvm")
    id("com.avast.gradle.docker-compose")
    id("com.github.ben-manes.versions")
    id("org.jlleitschuh.gradle.ktlint")
}

description = "RADAR Gateway to handle secured data flow to backend."
group = "org.radarbase"
version = "0.5.17-SNAPSHOT"

repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
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
    implementation(platform("com.fasterxml.jackson:jackson-bom:$jacksonVersion"))
    implementation("com.fasterxml.jackson.core:jackson-databind")

    val avroVersion: String by project
    runtimeOnly("org.apache.avro:avro:$avroVersion")

    val grizzlyVersion: String by project
    runtimeOnly("org.glassfish.grizzly:grizzly-framework-monitoring:$grizzlyVersion")
    runtimeOnly("org.glassfish.grizzly:grizzly-http-monitoring:$grizzlyVersion")
    runtimeOnly("org.glassfish.grizzly:grizzly-http-server-monitoring:$grizzlyVersion")

    val log4j2Version: String by project
    runtimeOnly("org.apache.logging.log4j:log4j-core:$log4j2Version")
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:$log4j2Version")
    runtimeOnly("org.apache.logging.log4j:log4j-jul:$log4j2Version")

    val junitVersion: String by project
    val okhttp3Version: String by project
    val radarSchemasVersion: String by project
    val mockitoKotlinVersion: String by project
    val hamcrestVersion: String by project
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.mockito.kotlin:mockito-kotlin:$mockitoKotlinVersion")
    testImplementation("com.squareup.okhttp3:mockwebserver:$okhttp3Version") {
        exclude(group = "junit", module = "junit")
    }
    testImplementation("org.hamcrest:hamcrest:$hamcrestVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

    testImplementation("org.radarbase:radar-schemas-commons:$radarSchemasVersion")
    integrationTestImplementation("com.squareup.okhttp3:okhttp:$okhttp3Version")
    integrationTestImplementation("org.radarbase:radar-schemas-commons:$radarSchemasVersion")
    integrationTestImplementation("org.radarbase:radar-commons-testing:$radarCommonsVersion")
}

val jvmTargetVersion = 17

tasks.withType<JavaCompile> {
    options.release.set(jvmTargetVersion)
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(jvmTargetVersion.toString()))
        apiVersion.set(KotlinVersion.KOTLIN_1_8)
        languageVersion.set(KotlinVersion.KOTLIN_1_8)
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

idea {
    module {
        isDownloadSources = true
    }
}

ktlint {
    val ktlintVersion: String by project
    version.set(ktlintVersion)
}

tasks.register("downloadDependencies") {
    doFirst {
        configurations["compileClasspath"].files
        configurations["runtimeClasspath"].files
        println("Downloaded all dependencies")
    }
    outputs.upToDateWhen { false }
}

tasks.register<Copy>("copyDependencies") {
    from(configurations.runtimeClasspath.map { it.files })
    into("$buildDir/third-party/")
    doLast {
        println("Copied third-party runtime dependencies")
    }
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA", "-CE").any { version.toUpperCase().contains(it) }
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
    gradleVersion = "8.0.1"
}
