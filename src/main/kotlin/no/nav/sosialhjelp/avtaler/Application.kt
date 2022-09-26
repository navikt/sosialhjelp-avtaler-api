package no.nav.sosialhjelp.avtaler

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.sosialhjelp.avtaler.avtaler.AvtaleService
import no.nav.sosialhjelp.avtaler.avtaler.avtaleApi
import no.nav.sosialhjelp.avtaler.internal.internalRoutes

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        setupRoutes()
    }.start(wait = true)
}

fun Application.setupRoutes() {
    routing {
        val avtaleService = AvtaleService()

        route("/sosialhjelp/avtaler-api") {
            internalRoutes()
            avtaleApi(avtaleService)
        }
    }
}
