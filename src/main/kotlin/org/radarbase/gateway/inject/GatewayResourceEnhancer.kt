package org.radarbase.gateway.inject

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.glassfish.jersey.internal.inject.AbstractBinder
import org.glassfish.jersey.internal.inject.PerThread
import org.glassfish.jersey.message.DeflateEncoder
import org.glassfish.jersey.message.GZipEncoder
import org.glassfish.jersey.server.filter.EncodingFilter
import org.radarbase.gateway.Config
import org.radarbase.gateway.io.AvroProcessor
import org.radarbase.gateway.io.AvroProcessorFactory
import org.radarbase.gateway.io.BinaryToAvroConverter
import org.radarbase.gateway.io.LzfseEncoder
import org.radarbase.gateway.kafka.KafkaAdminService
import org.radarbase.gateway.kafka.KafkaAdminServiceFactory
import org.radarbase.gateway.kafka.ProducerPool
import org.radarbase.gateway.kafka.ProducerPoolFactory
import org.radarbase.gateway.service.SchedulingService
import org.radarbase.gateway.service.SchedulingServiceFactory
import org.radarbase.jersey.auth.ProjectService
import org.radarbase.jersey.config.JerseyResourceEnhancer
import org.radarbase.producer.rest.SchemaRetriever
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

class GatewayResourceEnhancer(private val config: Config): JerseyResourceEnhancer {
    override val packages: Array<String> = arrayOf(
            "org.radarbase.gateway.filter",
            "org.radarbase.gateway.io",
            "org.radarbase.gateway.resource")

    override val classes: Array<Class<*>> = arrayOf(
            EncodingFilter::class.java,
            GZipEncoder::class.java,
            DeflateEncoder::class.java,
            LzfseEncoder::class.java)

    override fun enhanceBinder(binder: AbstractBinder) {
        binder.apply {
            bind(config)
                    .to(Config::class.java)

            bindFactory(AvroProcessorFactory::class.java)
                    .to(AvroProcessor::class.java)
                    .`in`(Singleton::class.java)

            bind(BinaryToAvroConverter::class.java)
                    .to(BinaryToAvroConverter::class.java)
                    .`in`(PerThread::class.java)

            // Bind factories.
            bindFactory(SchemaRetrieverFactory::class.java)
                    .to(SchemaRetriever::class.java)
                    .`in`(Singleton::class.java)

            bindFactory(SchedulingServiceFactory::class.java)
                    .to(SchedulingService::class.java)
                    .`in`(Singleton::class.java)

            bindFactory(ProducerPoolFactory::class.java)
                    .to(ProducerPool::class.java)
                    .`in`(Singleton::class.java)

            bindFactory(KafkaAdminServiceFactory::class.java)
                    .to(KafkaAdminService::class.java)
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
