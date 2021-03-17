plugins {
    id("com.github.johnrengelman.shadow") version "6.1.0"
    java
}

repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
}

dependencies {
    compile("org.apache.kafka:kafka-clients:${project.property("kafkaVersion")}") {
        isTransitive = true
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    compile("io.confluent:kafka-avro-serializer:${project.property("confluentVersion")}") {
        isTransitive = false
    }
    compile("io.confluent:kafka-schema-serializer:${project.property("confluentVersion")}") {
        isTransitive = false
    }
    compile("io.confluent:kafka-schema-registry-client:${project.property("confluentVersion")}") {
        isTransitive = false
    }
    compile("org.glassfish.jersey.core:jersey-common:2.31")
    compile("io.swagger:swagger-annotations:1.6.2")
    compile("io.confluent:common-utils:6.1.0") {
        isTransitive = false
    }
}

tasks.shadowJar {
    relocate("javax.ws.rs", "shadow.javax.ws.rs")
    relocate("org.glassfish", "shadow.org.glassfish")
}
