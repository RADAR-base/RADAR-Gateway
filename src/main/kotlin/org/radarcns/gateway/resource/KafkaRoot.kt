package org.radarcns.gateway.resource

import org.radarcns.gateway.io.ProxyClient
import javax.inject.Singleton
import javax.ws.rs.GET
import javax.ws.rs.HEAD
import javax.ws.rs.OPTIONS
import javax.ws.rs.Path
import javax.ws.rs.core.Context
import javax.ws.rs.core.Response

/** Root path, just forward requests without authentication. */
@Path("/")
@Singleton
class KafkaRoot {
    @Context
    private lateinit var proxyClient: ProxyClient

    @OPTIONS
    fun rootOptions(): Response = Response.noContent()
            .header("Allow", "HEAD,GET,OPTIONS")
            .build()

    @GET
    fun root() = proxyClient.proxyRequest("GET")

    @HEAD
    fun rootHead() = proxyClient.proxyRequest("HEAD")
}
