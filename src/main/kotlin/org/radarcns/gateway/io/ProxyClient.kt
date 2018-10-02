package org.radarcns.gateway.io

import okhttp3.*
import okio.BufferedSink
import okio.Okio
import org.radarcns.gateway.Config
import java.io.OutputStream
import javax.inject.Singleton
import javax.ws.rs.core.Context
import javax.ws.rs.core.HttpHeaders
import javax.ws.rs.core.StreamingOutput
import javax.ws.rs.core.UriInfo
import javax.ws.rs.ext.Provider

/**
 * Proxies requests to another server.
 *
 * @implNote this implementation is not thread-safe because it uses a object-level buffer.
 */
@Provider
@Singleton
class ProxyClient(@Context config: Config, @Context private val client: OkHttpClient,
        @Context private val uriInfo: UriInfo, @Context private val headers: HttpHeaders) {

    private val baseUrl = HttpUrl.parse(config.restProxyUrl)
            ?: throw IllegalArgumentException("Base URL ${config.restProxyUrl} is invalid")

    fun proxyRequest(method: String, headers: Headers, sinkWriter: ((BufferedSink) -> Unit)?): javax.ws.rs.core.Response {
        val request = createProxyRequest(method, uriInfo, headers, sinkWriter)

        val response = client.newCall(request).execute()

        try {
            val builder = javax.ws.rs.core.Response.status(response.code())

            response.headers().toMultimap().forEach { (name, values) ->
                values.forEach { value -> builder.header(name, value) }
            }

            val inputResponse = response.body()?.source()

            if (inputResponse != null) {
                builder.entity(StreamingOutput { outputResponse ->
                    response.use {
                        inputResponse.readAll(Okio.sink(outputResponse))
                        outputResponse?.flush()
                    }
                })
            } else {
                response.close()
            }
            return builder.build()
        } catch (ex: Exception) {
            response.close()
            throw ex
        }
    }

    fun proxyRequest(method: String, sinkWriter: ((BufferedSink) -> Unit)? = null): javax.ws.rs.core.Response {
        return proxyRequest(method, jerseyToOkHttpHeaders(headers).build(), sinkWriter)
    }

    private fun createProxyRequest(method: String, uriInfo: UriInfo, headers: Headers, sinkWriter: ((BufferedSink) -> Unit)?) : Request {
        val url = baseUrl.newBuilder(uriInfo.path)?.build()
                ?: throw IllegalArgumentException(
                        "Path $baseUrl/${uriInfo.path} is invalid")

        val body = if (sinkWriter != null) object : RequestBody() {
            override fun writeTo(sink: BufferedSink?) {
                sink?.let { sinkWriter(sink) }
                sink?.flush()
            }

            override fun contentType(): MediaType? {
                val type = headers.get("Content-Type")
                return if (type != null) MediaType.parse(type) else null
            }
        } else null

        return Request.Builder()
                .url(url)
                .headers(headers)
                .method(method, body)
                .build()
    }

    companion object {
        fun jerseyToOkHttpHeaders(headers: HttpHeaders): Headers.Builder = headers.requestHeaders
                .flatMap { entry -> entry.value.map { Pair(entry.key, it) } }
                .fold(Headers.Builder()) { builder, pair -> builder.add(pair.first, pair.second) }
    }
}
