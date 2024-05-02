package org.radarbase.gateway.config

import java.net.URI

data class GatewayServerConfig(
    /** Base URL to serve data with. This will determine the base path and the port. */
    val baseUri: URI = URI.create("http://0.0.0.0:8090/kafka/"),
    /** Maximum number of simultaneous requests. */
    val maxRequests: Int = 200,
    /**
     * Maximum request content length, also when decompressed.
     * This protects against memory overflows.
     */
    val maxRequestSize: Long = 24 * 1024 * 1024,
    /**
     * Whether JMX should be enabled. Disable if not needed, for higher performance.
     */
    val isJmxEnabled: Boolean = true,
)
