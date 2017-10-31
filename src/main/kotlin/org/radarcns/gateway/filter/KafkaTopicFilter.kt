package org.radarcns.gateway.filter

import org.radarcns.gateway.kafka.KafkaClient
import java.io.IOException
import java.time.Duration
import java.time.Instant
import javax.servlet.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

private val SUCCESS_TIMEOUT = Duration.ofHours(1)
private val FAILURE_TIMEOUT = Duration.ofMinutes(1)

class KafkaTopicFilter : Filter {
    private var context: ServletContext? = null
    private var client: KafkaClient? = null
    private var previousTopics: Set<String> = HashSet()
    private var fetchTime: Instant = Instant.EPOCH

    @Throws(ServletException::class)
    override fun init(filterConfig: FilterConfig) {
        this.context = filterConfig.servletContext
        this.context!!.log("KafkaTopicFilter initialized")
        val restProxyUrl = this.context!!.getInitParameter("restProxyUrl")
        this.client = KafkaClient(restProxyUrl)
        updateTopics()
    }

    @Throws(IOException::class, ServletException::class)
    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val req = request as HttpServletRequest

        if (!req.method.equals("POST", ignoreCase = true)) {
            chain.doFilter(request, response)
            return
        }

        if (Instant.now().isAfter(fetchTime.plus(SUCCESS_TIMEOUT))) {
            updateTopics()
        }

        val topic = req.requestURI.substringAfterLast('/')

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
            previousTopics = client!!.getSubjects()
            fetchTime = Instant.now()
            true
        } catch (ex: IOException) {
            this.context!!.log("Failed to retrieve subjects", ex)
            false
        }
    }

    override fun destroy() {}
}
