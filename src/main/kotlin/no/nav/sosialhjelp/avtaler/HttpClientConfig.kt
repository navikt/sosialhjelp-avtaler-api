package no.nav.sosialhjelp.avtaler

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson

object HttpClientConfig {
    fun httpClient(engine: HttpClientEngine = CIO.create()): HttpClient =
        HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) {
                jackson {
                    registerModule(JavaTimeModule())
                    disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                }
            }
            install(HttpTimeout)
        }
}

fun engineFactory(block: () -> HttpClientEngine): HttpClientEngine =
    when (Configuration.profile) {
        Configuration.Profile.LOCAL -> block()
        else -> CIO.create()
    }

fun defaultHttpClient(): HttpClient {
    return HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson {
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            }
        }
    }
}

fun defaultHttpClientWithJsonHeaders(): HttpClient {
    return defaultHttpClient()
        .config {
            defaultRequest {
                jsonHeaders()
            }
        }
}

fun DefaultRequest.DefaultRequestBuilder.jsonHeaders() {
    headers {
        accept(ContentType.Application.Json)
        contentType(ContentType.Application.Json)
    }
}
