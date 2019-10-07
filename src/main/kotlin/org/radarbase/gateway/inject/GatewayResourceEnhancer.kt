package org.radarbase.gateway.inject

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.glassfish.jersey.internal.inject.AbstractBinder
import org.glassfish.jersey.internal.inject.PerThread
import org.glassfish.jersey.server.ResourceConfig
import org.radarbase.gateway.Config
import org.radarbase.gateway.io.AvroProcessor
import org.radarbase.gateway.io.BinaryToAvroConverter
import org.radarbase.gateway.io.ProxyClient
import org.radarbase.jersey.auth.ProjectService
import org.radarbase.jersey.config.JerseyResourceEnhancer
import org.radarbase.producer.rest.SchemaRetriever
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

class GatewayResourceEnhancer(private val config: Config): JerseyResourceEnhancer {
    override fun enhanceResources(resourceConfig: ResourceConfig) {
        resourceConfig.packages(
                "org.radarbase.gateway.filter",
                "org.radarbase.gateway.io",
                "org.radarbase.gateway.resource")
    }

    override fun enhanceBinder(binder: AbstractBinder) {
        binder.apply {
            bind(config)
                    .to(Config::class.java)

            bind(OkHttpClient.Builder()
                    .readTimeout(1, TimeUnit.MINUTES)
                    .writeTimeout(1, TimeUnit.MINUTES)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .dispatcher(Dispatcher().apply {
                        maxRequests = config.maxRequests
                        maxRequestsPerHost = config.maxRequests
                    })
                    .connectionPool(ConnectionPool(config.maxRequests, 5, TimeUnit.MINUTES))
                    .build())
                    .to(OkHttpClient::class.java)

            // Bind specific classes. These can use injects.
            bind(ProxyClient::class.java)
                    .to(ProxyClient::class.java)
                    .`in`(Singleton::class.java)

            bind(AvroProcessor::class.java)
                    .to(AvroProcessor::class.java)
                    .`in`(Singleton::class.java)

            bind(BinaryToAvroConverter::class.java)
                    .to(BinaryToAvroConverter::class.java)
                    .`in`(PerThread::class.java)

            // Bind factories.
            bindFactory(SchemaRetrieverFactory::class.java)
                    .to(SchemaRetriever::class.java)
                    .`in`(Singleton::class.java)

            val unverifiedProjectService = object : ProjectService {
                // no validation done
                override fun ensureProject(projectId: String) = Unit
            }
            bind(unverifiedProjectService)
                    .to(ProjectService::class.java)
        }
    }
}
