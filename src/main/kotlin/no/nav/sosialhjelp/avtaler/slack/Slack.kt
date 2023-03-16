package no.nav.sosialhjelp.avtaler.slack

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.sosialhjelp.avtaler.Configuration
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

private val log = LoggerFactory.getLogger("PostToSlack")

object Slack {
    private val username = "sosialhjelp-avtaler-api"
    private val environment = Configuration.slackProperties.environment
    private val hookUrl = Configuration.slackProperties.slackHook
    private val channelProd = "#digisos-avtaler-alerts"
    private val channelDev = "#digisos-alerts-dev"

    fun post(message: String) {
        try {
            val slackMessage = "${environment.uppercase()} - $message"
            val values = mapOf(
                "text" to slackMessage,
                "channel" to if (Configuration.prod) channelProd else channelDev,
                "username" to username,
            )

            log.info("Debug slack channel: $channelDev, lengde url: ${hookUrl.length}, short url:${hookUrl.take(50)} ")

            val objectMapper = ObjectMapper()
            val requestBody: String = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(values)

            val client = HttpClient.newBuilder().build()
            val request = HttpRequest.newBuilder()
                .uri(URI.create(hookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            log.warn("Posting av varsel til slack feilet.", e)
        }
    }
}
