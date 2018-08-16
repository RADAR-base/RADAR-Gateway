package org.radarcns.gateway.inject

import okhttp3.OkHttpClient
import org.glassfish.jersey.internal.inject.AbstractBinder
import org.glassfish.jersey.internal.inject.PerThread
import org.glassfish.jersey.process.internal.RequestScoped
import org.radarcns.auth.authentication.TokenValidator
import org.radarcns.auth.token.RadarToken
import org.radarcns.gateway.Config
import org.radarcns.gateway.io.AvroProcessor
import org.radarcns.gateway.io.BinaryToAvroConverter
import org.radarcns.gateway.io.ProxyClient
import org.radarcns.producer.rest.SchemaRetriever
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** This binder needs to register all non-Jersey classes, otherwise initialization fails. */
class GatewayBinder(private val config: Config) : AbstractBinder() {
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
        bindFactory(RadarTokenFactory::class.java)
                .proxy(true)
                .proxyForSameScope(false)
                .to(RadarToken::class.java)
                .`in`(RequestScoped::class.java)

        bindFactory(TokenValidatorFactory::class.java)
                .to(TokenValidator::class.java)
                .`in`(Singleton::class.java)

        bindFactory(SchemaRetrieverFactory::class.java)
                .to(SchemaRetriever::class.java)
                .`in`(Singleton::class.java)
    }
}
