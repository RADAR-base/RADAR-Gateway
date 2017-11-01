package org.radarcns.gateway.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectReader
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class KafkaClient(private val restProxyBaseUrl: String) {
    private val client: OkHttpClient = OkHttpClient.Builder().build()
    private val stringArrayReader: ObjectReader = ObjectMapper().readerFor(Array<String>::class.java)

    @Throws(IOException::class)
    fun getSubjects(): Set<String> {
        val request = Request.Builder().url(restProxyBaseUrl + "/topics").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Cannot query rest proxy url: " + response.code())
            }
            val input = response.body()?.byteStream() ?: throw IOException(
                    "Rest proxy did not return any data")

            stringArrayReader.readValue<Array<String>>(input).toSet()
        }
    }
}