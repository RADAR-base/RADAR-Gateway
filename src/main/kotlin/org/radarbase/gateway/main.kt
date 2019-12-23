package org.radarbase.gateway

import org.radarbase.jersey.GrizzlyServer
import org.radarbase.jersey.config.ConfigLoader

fun main(args: Array<String>) {
    val config: Config = ConfigLoader.loadConfig("gateway.yml", args)

    val resources = ConfigLoader.loadResources(config.resourceConfig, config)
    val server = GrizzlyServer(config.baseUri, resources, config.isJmxEnabled)
    server.listen()
}
