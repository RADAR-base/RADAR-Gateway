package org.radarcns.gateway.filter

import org.radarcns.gateway.kafka.KafkaClient
import java.io.IOException
import java.time.Duration
import java.time.Instant
import javax.servlet.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Asserts that data is only submitted to Kafka topics that already exist.
 */
class KafkaTopicFilter : Filter {
    private lateinit var context: ServletContext
    private lateinit var client: KafkaClient
    private var previousTopics: Set<String> = HashSet()
    private var fetchTime: Instant = Instant.EPOCH

    @Throws(ServletException::class)
    override fun init(filterConfig: FilterConfig) {
        this.context = filterConfig.servletContext

        val restProxyUrl = filterConfig.getInitParameter("targetUri")
        this.client = KafkaClient(restProxyUrl)
        updateTopics()

        this.context.log("KafkaTopicFilter initialized")
    }

    @Throws(IOException::class, ServletException::class)
    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val req = request as HttpServletRequest

        if (!req.method.equals("POST", ignoreCase = true)) {
            chain.doFilter(request, response)
            return
        }

        // invalidate removed topics
        if (Instant.now().isAfter(fetchTime.plus(SUCCESS_TIMEOUT))) {
            updateTopics()
        }

        val topic = req.requestURI.substringAfterLast('/')

        // topic exists or exists after an update
        if (previousTopics.contains(topic) || (
                Instant.now().isAfter(fetchTime.plus(FAILURE_TIMEOUT))
                && updateTopics()
                && previousTopics.contains(topic))) {
            chain.doFilter(request, response)
        } else {
            val res = response as HttpServletResponse
            res.setStatus(HttpServletResponse.SC_NOT_FOUND)
        }
    }

    private fun updateTopics(): Boolean {
        return try {
            previousTopics = client.getSubjects()
            fetchTime = Instant.now()
            true
        } catch (ex: IOException) {
            this.context.log("Failed to retrieve subjects", ex)
            false
        }
    }

    override fun destroy() {}

    companion object Constants {
        private val SUCCESS_TIMEOUT = Duration.ofHours(1)
        private val FAILURE_TIMEOUT = Duration.ofMinutes(1)
    }
}
