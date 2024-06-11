package org.radarbase.gateway.resource

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class KafkaRootTest {
    private lateinit var httpClient: HttpClient

    @BeforeEach
    fun setUpClass() {
        httpClient = HttpClient(CIO)
    }

    @Test
    fun queryRoot() = runBlocking {
        assertThat(
            httpClient.get(url = Url("$BASE_URI/")).status,
            equalTo(HttpStatusCode.OK),
        )
        assertThat(
            httpClient.head(url = Url("$BASE_URI/")).status,
            equalTo(HttpStatusCode.OK),
        )
    }

    companion object {
        const val BASE_URI = "http://localhost:8092/radar-gateway"
    }
}
