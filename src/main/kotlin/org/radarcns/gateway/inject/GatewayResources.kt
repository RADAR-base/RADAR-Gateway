package org.radarcns.gateway.inject

import okhttp3.OkHttpClient
import org.glassfish.jersey.internal.inject.AbstractBinder
import org.glassfish.jersey.internal.inject.PerThread
import org.glassfish.jersey.process.internal.RequestScoped
import org.glassfish.jersey.server.ResourceConfig
import org.radarcns.auth.authentication.TokenValidator
import org.radarcns.gateway.Config
import org.radarcns.gateway.auth.Auth
import org.radarcns.gateway.auth.AuthenticationFilter
import org.radarcns.gateway.io.AvroProcessor
import org.radarcns.gateway.io.BinaryToAvroConverter
import org.radarcns.gateway.io.ProxyClient
import org.radarcns.producer.rest.SchemaRetriever
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

interface GatewayResources {
    fun getResources(config: Config): ResourceConfig {
        val resources = ResourceConfig()
        resources.packages(
                "org.radarcns.gateway.auth",
                "org.radarcns.gateway.exception",
                "org.radarcns.gateway.filter",
                "org.radarcns.gateway.io",
                "org.radarcns.gateway.resource")
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
