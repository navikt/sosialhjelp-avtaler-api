package no.nav.sosialhjelp.avtaler

import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.sosialhjelp.avtaler.avtaler.AvtaleService
import no.nav.sosialhjelp.avtaler.avtaler.avtaleApi
import no.nav.sosialhjelp.avtaler.internal.internalRoutes
import java.util.TimeZone

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configure()
        setupRoutes()
    }.start(wait = true)
}

fun Application.configure() {
    TimeZone.setDefault(TimeZone.getTimeZone("Europe/Oslo"))
    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }
}

fun Application.setupRoutes() {
    routing {
        route("/sosialhjelp/avtaler-api") {
            internalRoutes()

            val avtaleService = AvtaleService()

            route("/api") {
                avtaleApi(avtaleService)
            }
        }
    }
}
