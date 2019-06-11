package org.radarbase.gateway.inject

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.glassfish.jersey.internal.inject.AbstractBinder
import org.glassfish.jersey.internal.inject.PerThread
import org.glassfish.jersey.process.internal.RequestScoped
import org.glassfish.jersey.server.ResourceConfig
import org.radarbase.producer.rest.SchemaRetriever
import org.radarbase.gateway.Config
import org.radarbase.gateway.auth.Auth
import org.radarbase.gateway.auth.AuthenticationFilter
import org.radarbase.gateway.exception.HttpApplicationExceptionMapper
import org.radarbase.gateway.exception.UnhandledExceptionMapper
import org.radarbase.gateway.exception.WebApplicationExceptionMapper
import org.radarbase.gateway.filter.AuthorizationFeature
import org.radarbase.gateway.filter.KafkaTopicFilter
import org.radarbase.gateway.io.*
import org.radarbase.gateway.resource.KafkaRoot
import org.radarbase.gateway.resource.KafkaTopics
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

interface GatewayResources {
    fun getResources(config: Config): ResourceConfig {
        val resources = ResourceConfig(
                AuthenticationFilter::class.java,
                UnhandledExceptionMapper::class.java,
                WebApplicationExceptionMapper::class.java,
                HttpApplicationExceptionMapper::class.java,
                AuthorizationFeature::class.java,
                KafkaTopicFilter::class.java,
                AvroJsonReader::class.java,
                SizeLimitInterceptor::class.java,
                DecompressInterceptor::class.java,
                KafkaRoot::class.java,
                KafkaTopics::class.java)
        resources.register(getBinder(config))
        resources.property("jersey.config.server.wadl.disableWadl", true)
        registerAuthentication(resources)
        return resources
    }

    fun registerAuthentication(resources: ResourceConfig)

    fun registerAuthenticationUtilities(binder: AbstractBinder)

    fun getBinder(config: Config) = object : AbstractBinder() {
        override fun configure() {
            // Bind instances. These cannot use any injects themselves
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
            bindFactory(AuthFactory::class.java)
                    .proxy(true)
                    .proxyForSameScope(false)
                    .to(Auth::class.java)
                    .`in`(RequestScoped::class.java)

            bindFactory(SchemaRetrieverFactory::class.java)
                    .to(SchemaRetriever::class.java)
                    .`in`(Singleton::class.java)

            registerAuthenticationUtilities(this)
        }
    }
}
