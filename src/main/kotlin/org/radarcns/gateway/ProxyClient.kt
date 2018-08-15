package org.radarcns.gateway

import okhttp3.*
import okio.BufferedSink
import javax.ws.rs.core.Context
import javax.ws.rs.core.HttpHeaders
import javax.ws.rs.core.StreamingOutput
import javax.ws.rs.core.UriInfo

/**
 * Proxies requests to another server.
 *
 * @implNote this implementation is not thread-safe because it uses a object-level buffer.
 */
class ProxyClient constructor(@Context config: Config, @Context private val client: OkHttpClient) {
    private val baseUrl = HttpUrl.parse(config.restProxyUrl)
            ?: throw IllegalArgumentException("Base URL ${config.restProxyUrl} invalid")

    private val buffer = ByteArray(64 * 1024)

    fun proxyRequest(method: String, uriInfo: UriInfo, headers: Headers, sinkWriter: ((BufferedSink) -> Unit)?): javax.ws.rs.core.Response {
        val request = createProxyRequest(method, uriInfo, headers, sinkWriter)

        val response = client.newCall(request).execute()

        val builder = javax.ws.rs.core.Response.status(response.code())

        response.headers().toMultimap().forEach {(name, values) ->
            values.forEach { value -> builder.header(name, value) }
        }

        val inputResponse = response.body()?.byteStream()
        if (inputResponse != null) {
            builder.entity(StreamingOutput { outputResponse ->
                do {
                    val nRead = inputResponse.read(buffer)
                    if (nRead == -1) break
                    outputResponse?.write(buffer, 0, nRead)
                } while (true)

                outputResponse?.flush()
                response.close()
            })
        } else {
            response.close()
        }
        return builder.build()
    }

    fun proxyRequest(method: String, uriInfo: UriInfo, headers: HttpHeaders, sinkWriter: ((BufferedSink) -> Unit)?): javax.ws.rs.core.Response {
        return proxyRequest(method, uriInfo, jerseyToOkHttpHeaders(headers).build(), sinkWriter)
    }

    private fun createProxyRequest(method: String, uriInfo: UriInfo, headers: Headers, sinkWriter: ((BufferedSink) -> Unit)?) : Request {
        val url = baseUrl.newBuilder(uriInfo.path)?.build()
                ?: throw IllegalArgumentException("Path $baseUrl/${uriInfo.path} is invalid")

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
