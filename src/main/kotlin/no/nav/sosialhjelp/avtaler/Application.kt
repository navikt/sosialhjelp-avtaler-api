package no.nav.sosialhjelp.avtaler

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import mu.KotlinLogging
import no.nav.sosialhjelp.avtaler.altinn.AltinnService
import no.nav.sosialhjelp.avtaler.avtaler.AvtaleService
import no.nav.sosialhjelp.avtaler.avtaler.avtaleApi
import no.nav.sosialhjelp.avtaler.internal.internalRoutes
import no.nav.sosialhjelp.avtaler.kommune.kommuneApi
import java.util.TimeZone

private val log = KotlinLogging.logger {}

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        log.info("sosialhjelp-avtaler-api starting up...")
        configure()
        setupRoutes()
    }.start(wait = true)
}

fun Application.configure() {
    TimeZone.setDefault(TimeZone.getTimeZone("Europe/Oslo"))
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }
}

fun Application.setupRoutes() {
    routing {
        route("/sosialhjelp/avtaler-api") {
            internalRoutes()

            val avtaleService = AvtaleService()
            val altinnService = AltinnService()

            route("/api") {
                avtaleApi(avtaleService)
                kommuneApi(avtaleService, altinnService)
            }
        }
    }
}
// curl -X POST  --header "Content-Type: application/json" --data  '{"orgnr": "0000"}' -v http://localhost:8080/sosialhjelp/avtaler-api/api/avtale/
