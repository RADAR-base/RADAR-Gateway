package org.radarcns.gateway

import okhttp3.OkHttpClient
import org.glassfish.grizzly.http.server.HttpServer
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory
import org.glassfish.jersey.internal.inject.AbstractBinder
import org.glassfish.jersey.process.internal.RequestScoped
import org.glassfish.jersey.server.ResourceConfig
import org.radarcns.auth.authentication.TokenValidator
import org.radarcns.auth.token.RadarToken
import org.radarcns.gateway.inject.RadarTokenFactory
import org.radarcns.gateway.inject.TokenValidatorFactory
import org.radarcns.gateway.reader.AvroJsonReader
import org.radarcns.gateway.resource.KafkaTopics
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

class GrizzlyServer(private val config: Config) {
    private var httpServer: HttpServer? = null

    private fun getResourceConfig(): ResourceConfig {
        val resources = ResourceConfig()
        resources.packages(
                "org.radarcns.gateway.exception",
                "org.radarcns.gateway.filter",
                "org.radarcns.gateway.reader")
        resources.register(KafkaTopics::class.java)
        resources.register(object : AbstractBinder() {
            override fun configure() {
                bind(config)
                        .to(Config::class.java)

                bindFactory(RadarTokenFactory::class.java)
                        .proxy(true)
                        .proxyForSameScope(false)
                        .to(RadarToken::class.java)
                        .`in`(RequestScoped::class.java)

                bind(OkHttpClient.Builder()
                        .readTimeout(1, TimeUnit.MINUTES)
                        .writeTimeout(1, TimeUnit.MINUTES)
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .build())
                        .to(OkHttpClient::class.java)

                bindFactory(TokenValidatorFactory::class.java)
                        .to(TokenValidator::class.java)
                        .`in`(Singleton::class.java)

                bind(ProxyClient::class.java)
                        .to(ProxyClient::class.java)
                        .`in`(Singleton::class.java)
            }
        })
        return resources
    }

    fun start() {
        httpServer = GrizzlyHttpServerFactory.createHttpServer(config.baseUri, getResourceConfig())
        httpServer!!.start()
    }

    fun shutdown() {
        httpServer?.shutdown()?.get(5, TimeUnit.SECONDS)
    }
}
