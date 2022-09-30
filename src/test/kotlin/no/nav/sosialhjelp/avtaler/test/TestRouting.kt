package no.nav.hjelpemidler.brille.test

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.kotest.common.runBlocking
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import io.ktor.server.auth.authentication
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.routing.Routing
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplication
import kotlinx.coroutines.runBlocking
import no.nav.sosialhjelp.avtaler.UserPrincipal
import no.nav.sosialhjelp.avtaler.configure

class TestRouting(configuration: Routing.() -> Unit) {
    private val application = TestApplication {
        environment {
            config = MapApplicationConfig() // for at application.conf ikke skal leses
        }
        application {
            configure()
            authentication {
                provider("test") {
                    authenticate { context ->
                        context.principal(principal)
                    }
                }
            }
            routing(configuration)
        }
    }

    internal val principal = UserPrincipal("15084300133")

    internal val client = application.createClient {
        install(ContentNegotiation) {
            jackson {
                registerModule(JavaTimeModule())
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            }
        }
        defaultRequest {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
        }
    }

    internal fun test(block: suspend TestRouting.() -> Unit) = runBlocking {
        block(this)
    }
}
