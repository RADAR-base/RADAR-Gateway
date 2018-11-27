package org.radarcns.gateway

import org.glassfish.grizzly.http.server.HttpServer
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory
import org.radarcns.gateway.inject.GatewayResources
import java.util.concurrent.TimeUnit

class GrizzlyServer(private val config: Config) {
    private var httpServer: HttpServer? = null

    fun start() {
        val gatewayResources = config.resourceConfig.getConstructor().newInstance()
        val resourceConfig = (gatewayResources as GatewayResources).getResources(config)

        httpServer = GrizzlyHttpServerFactory.createHttpServer(config.baseUri, resourceConfig)
        httpServer!!.start()
    }

    fun shutdown() {
        httpServer?.shutdown()?.get(5, TimeUnit.SECONDS)
    }
}
