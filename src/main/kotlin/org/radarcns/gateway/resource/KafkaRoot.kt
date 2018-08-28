package org.radarcns.gateway.resource

import org.radarcns.gateway.io.ProxyClient
import javax.inject.Singleton
import javax.ws.rs.GET
import javax.ws.rs.HEAD
import javax.ws.rs.Path
import javax.ws.rs.core.Context

/** Root path, just forward requests without authentication. */
@Path("/")
@Singleton
class KafkaRoot {
    @Context
    private lateinit var proxyClient: ProxyClient

    @GET
    fun root() = proxyClient.proxyRequest("GET")

    @HEAD
    fun rootHead() = proxyClient.proxyRequest("HEAD")
}
