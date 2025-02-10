package org.radarbase.gateway

import org.radarbase.gateway.config.GatewayConfig
import org.radarbase.jersey.GrizzlyServer
import org.radarbase.jersey.config.ConfigLoader
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager")

    val logger = LoggerFactory.getLogger("org.radarbase.gateway.MainKt")

    val config = try {
        ConfigLoader.loadConfig<GatewayConfig>(
            listOf(
                "gateway.yml",
                "/etc/radar-gateway/gateway.yml",
            ),
            args,
        ).withDefaults()
    } catch (ex: IllegalArgumentException) {
        logger.error("No configuration file was found.")
        logger.error("Usage: radar-gateway <config-file>")
        exitProcess(1)
    }

    try {
        config.validate()
        config.checkEnvironmentVars()
    } catch (ex: IllegalStateException) {
        logger.error("Configuration incomplete: {}", ex.message)
        exitProcess(1)
    }

    val resources = ConfigLoader.loadResources(config.resourceConfig, config)
    val server = GrizzlyServer(config.server.baseUri, resources, config.server.isJmxEnabled)
    server.listen()
}
