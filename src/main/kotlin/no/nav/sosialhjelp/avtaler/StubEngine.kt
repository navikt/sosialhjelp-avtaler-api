package no.nav.sosialhjelp.avtaler

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import mu.KotlinLogging

private val log = KotlinLogging.logger { }

class MockRoute(
    val url: String,
    val method: HttpMethod,
    val handler: MockRequestHandler,
)

class MockEngineBuilder(private val routes: MutableList<MockRoute> = mutableListOf()) : List<MockRoute> by routes {
    private fun add(
        url: String,
        method: HttpMethod,
        configure: MockRequestHandler,
    ) = routes.add(MockRoute(url = url, method = method, handler = configure))

    fun get(url: String, configure: MockRequestHandler) =
        add(url = url, method = HttpMethod.Get, configure = configure)

    fun post(url: String, configure: MockRequestHandler) =
        add(url = url, method = HttpMethod.Post, configure = configure)

    fun findOrElse(
        request: HttpRequestData,
        fallback: MockRequestHandleScope.(request: HttpRequestData) -> HttpResponseData,
    ): MockRoute =
        firstOrNull {
            it.url == request.url.encodedPath && it.method == request.method
        } ?: MockRoute(request.url.encodedPath, request.method, fallback)
}

object StubEngine {
    private fun <T> MockRequestHandleScope.respond(body: T): HttpResponseData =
        respond(
            jsonMapper.writeValueAsString(body),
            HttpStatusCode.OK,
            headersOf(HttpHeaders.ContentType, "application/json")
        )

    private fun mockEngineBuilder(block: MockEngineBuilder.() -> Unit): MockEngineBuilder =
        MockEngineBuilder().apply(block)

    private fun mockEngine(block: MockEngineBuilder.() -> Unit): HttpClientEngine = MockEngine { request ->
        log.info { "Svarer på ${request.method.value} ${request.url}" }
        mockEngineBuilder(block)
            .findOrElse(request) { respondError(HttpStatusCode.NotFound) }
            .handler(this, request)
    }

    fun tokenX(): HttpClientEngine = mockEngine {
        get("/default/.well-known/openid-configuration") {
            respond(
                mapOf(
                    "issuer" to "http://localhost:8080/default",
                    "jwks_uri" to "http://localhost:8080/default/jwks"
                )
            )
        }
    }
}
