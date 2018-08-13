package org.radarcns.gateway.resource

import org.radarcns.gateway.ProxyClient
import javax.inject.Singleton
import javax.ws.rs.GET
import javax.ws.rs.HEAD
import javax.ws.rs.Path
import javax.ws.rs.core.Context
import javax.ws.rs.core.HttpHeaders
import javax.ws.rs.core.UriInfo

/** Root path, just forward requests without authentication. */
@Path("/")
@Singleton
class KafkaRoot {
    @Context
    private lateinit var proxyClient: ProxyClient

    @Context
    private lateinit var uriInfo: UriInfo

    @Context
    private lateinit var headers: HttpHeaders

    @GET
    fun root() = proxyClient.proxyRequest("GET", uriInfo, headers, null)

    @HEAD
    fun rootHead() = proxyClient.proxyRequest("HEAD", uriInfo, headers, null)
}
