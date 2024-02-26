package org.radarbase.gateway.inject

import jakarta.inject.Singleton
import org.glassfish.jersey.internal.inject.AbstractBinder
import org.glassfish.jersey.internal.inject.PerThread
import org.glassfish.jersey.message.DeflateEncoder
import org.glassfish.jersey.message.GZipEncoder
import org.glassfish.jersey.server.filter.EncodingFilter
import org.radarbase.gateway.config.GatewayConfig
import org.radarbase.gateway.filter.KafkaTopicsAuthFilter
import org.radarbase.gateway.io.AvroProcessor
import org.radarbase.gateway.io.AvroProcessorFactory
import org.radarbase.gateway.io.BinaryToAvroConverter
import org.radarbase.gateway.io.LzfseEncoder
import org.radarbase.gateway.kafka.*
import org.radarbase.gateway.service.SchedulingService
import org.radarbase.gateway.service.SchedulingServiceFactory
import org.radarbase.jersey.enhancer.JerseyResourceEnhancer
import org.radarbase.jersey.filter.Filters
import org.radarbase.jersey.service.HealthService
import org.radarbase.jersey.service.ProjectService
import org.radarbase.producer.schema.SchemaRetriever

class GatewayResourceEnhancer(private val config: GatewayConfig) : JerseyResourceEnhancer {
    override val packages: Array<String> = arrayOf(
        "org.radarbase.gateway.filter",
        "org.radarbase.gateway.io",
        "org.radarbase.gateway.resource",
    )

    override val classes: Array<Class<*>> = buildList(6) {
        add(EncodingFilter::class.java)
        add(GZipEncoder::class.java)
        add(DeflateEncoder::class.java)
        add(LzfseEncoder::class.java)
        add(Filters.logResponse)

        if (config.auth.authorizeListTopics) {
            add(KafkaTopicsAuthFilter::class.java)
        }
    }.toTypedArray()

    override fun AbstractBinder.enhance() {
        bind(config)
            .to(GatewayConfig::class.java)

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

        bind(UnverifiedProjectService::class.java)
            .to(ProjectService::class.java)
            .`in`(Singleton::class.java)

        bind(KafkaHealthMetric::class.java)
            .named("kafka")
            .to(HealthService.Metric::class.java)
            .`in`(Singleton::class.java)
    }

    /** Project service without validation of the project's existence. */
    class UnverifiedProjectService : ProjectService {
        override suspend fun ensureOrganization(organizationId: String) = Unit

        override suspend fun ensureProject(projectId: String) = Unit

        override suspend fun ensureSubject(projectId: String, userId: String) = Unit

        override suspend fun listProjects(organizationId: String): List<String> = emptyList()

        override suspend fun projectOrganization(projectId: String): String = "main"
    }
}
