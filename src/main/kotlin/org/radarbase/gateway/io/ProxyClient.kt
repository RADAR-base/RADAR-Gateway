package org.radarbase.gateway.io

import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okio.BufferedSink
import okio.sink
import org.radarbase.gateway.Config
import org.radarbase.jersey.exception.HttpBadGatewayException
import org.radarbase.jersey.exception.HttpGatewayTimeoutException
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.ws.rs.core.Context
import javax.ws.rs.core.HttpHeaders
import javax.ws.rs.core.StreamingOutput
import javax.ws.rs.core.UriInfo

/**
 * Proxies requests to another server.
 *
 * Requests that do not get a response within 30 seconds are cancelled.
 *
 * @implNote this implementation is not thread-safe because it uses a object-level buffer.
 */

typealias JavaxResponse = javax.ws.rs.core.Response

class ProxyClient(
        @Context config: Config,
        @Context private val client: OkHttpClient,
        @Context private val uriInfo: UriInfo,
        @Context private val headers: HttpHeaders,
        @Context private val executor: ScheduledExecutorService
) {

    private val baseUrl = requireNotNull(config.restProxyUrl.toHttpUrlOrNull()) {
        "Base URL ${config.restProxyUrl} is invalid"
    }

    fun proxyRequest(method: String, headers: Headers, sinkWriter: ((BufferedSink) -> Unit)?): JavaxResponse {
        val request = createProxyRequest(method, uriInfo, headers, sinkWriter)

        val response = try {
            client.newCall(request).execute()
        } catch (ex: SocketTimeoutException) {
            throw HttpGatewayTimeoutException("Connection to Kafka REST proxy timed out")
        } catch (ex: IOException) {
            logger.error("Failed to connecto to Kafka REST proxy: {}", ex.toString())
            throw HttpBadGatewayException("Failed to connect to Kafka REST proxy")
        }

        val didStart = AtomicBoolean(false)

        // close the stream if it does not start within 30 seconds to avoid lingering connections.
        val compareFuture = executor.schedule({
            if (didStart.compareAndSet(false, true)) {
                response.close()
            }
        }, 30, TimeUnit.SECONDS)

        try {
            val builder = javax.ws.rs.core.Response.status(response.code)

            response.headers.toMultimap().forEach { (name, values) ->
                values.forEach { value -> builder.header(name, value) }
            }

            logger.info("[{}] {} {} - {}", response.code, method, request.url.encodedPath, response.header("Content-Length") ?: 0)

            val inputResponse = response.body?.source()

            if (inputResponse != null) {
                builder.entity(StreamingOutput { outputResponse ->
                    if (!didStart.compareAndSet(false, true)) {
                        // do not try to stream, the underlying stream is already closed.
                        logger.error("Failed to start streaming within 30 seconds")
                        return@StreamingOutput
                    }
                    compareFuture.cancel(false)
                    response.use {
                        inputResponse.readAll(outputResponse.sink())
                        outputResponse?.flush()
                    }
                })
            } else {
                response.close()
                compareFuture.cancel(false)
            }
            return builder.build()
        } catch (ex: Exception) {
            compareFuture.cancel(false)
            response.close()
            throw ex
        }
    }

    fun proxyRequest(method: String, sinkWriter: ((BufferedSink) -> Unit)? = null): JavaxResponse =
            proxyRequest(method, jerseyToOkHttpHeaders(headers).build(), sinkWriter)

    private fun createProxyRequest(method: String, uriInfo: UriInfo, headers: Headers, sinkWriter: ((BufferedSink) -> Unit)?) : Request {
        val url = baseUrl.newBuilder(uriInfo.path)?.build()
                ?: throw IllegalArgumentException("Path $baseUrl/${uriInfo.path} is invalid")

        val body = if (sinkWriter != null) object : RequestBody() {
            override fun writeTo(sink: BufferedSink) {
                sinkWriter(sink)
                sink.flush()
            }
            override fun isOneShot() = true
            override fun contentType() = headers["Content-Type"]?.toMediaType()
        } else null

        return Request.Builder()
                .url(url)
                .headers(headers)
                .method(method, body)
                .build()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ProxyClient::class.java)

        fun jerseyToOkHttpHeaders(headers: HttpHeaders): Headers.Builder = headers.requestHeaders
                .flatMap { entry -> entry.value.map { entry.key to it } }
                .fold(Headers.Builder()) { builder, pair -> builder.add(pair.first, pair.second) }
    }
}
