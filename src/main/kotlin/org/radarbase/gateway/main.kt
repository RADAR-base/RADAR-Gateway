package org.radarbase.gateway

import org.radarbase.jersey.GrizzlyServer
import org.radarbase.jersey.config.ConfigLoader

fun main(args: Array<String>) {
    val config: Config = ConfigLoader.loadConfig(listOf(
            "gateway.yml",
            "/etc/radar-gateway/gateway.yml"), args)

    val resources = ConfigLoader.loadResources(config.resourceConfig, config)
    val server = GrizzlyServer(config.server.baseUri, resources, config.server.isJmxEnabled)
    server.listen()
}
