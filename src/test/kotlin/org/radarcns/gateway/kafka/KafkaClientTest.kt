package org.radarcns.gateway.kafka

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class KafkaClientTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Test
    fun getSubjects() {
        val client = KafkaClient(server.url("/").toString())
        val topics = arrayOf("a", "b", "c", "a")
        server.enqueue(MockResponse().setBody(topics.map { '"' + it + '"' }.toString()))
        assertEquals(topics.toSet(), client.getSubjects())
    }
}