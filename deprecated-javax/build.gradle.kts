plugins {
    id("com.github.johnrengelman.shadow") version "6.1.0"
    java
}

repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
}

dependencies {
    implementation("org.apache.kafka:kafka-clients:${project.property("kafkaVersion")}") {
        isTransitive = true
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("io.confluent:kafka-avro-serializer:${project.property("confluentVersion")}") {
        isTransitive = false
    }
    implementation("io.confluent:kafka-schema-serializer:${project.property("confluentVersion")}") {
        isTransitive = false
    }
    implementation("io.confluent:kafka-schema-registry-client:${project.property("confluentVersion")}") {
        isTransitive = false
    }
    implementation("org.glassfish.jersey.core:jersey-common:2.31")
    implementation("io.swagger:swagger-annotations:1.6.2")
    implementation("io.confluent:common-utils:6.1.0") {
        isTransitive = false
    }
}

tasks.shadowJar {
    configurations = listOf(project.configurations.compileClasspath.get())
    relocate("javax.ws.rs", "shadow.javax.ws.rs")
    relocate("org.glassfish", "shadow.org.glassfish")
}
