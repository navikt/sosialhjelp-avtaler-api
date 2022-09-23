package no.nav.sosialhjelp.avtaler

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import no.nav.sosialhjelp.avtaler.internal.internalRoutes

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        setupRoutes()
    }.start(wait = true)
}

fun Application.setupRoutes() {

    routing {
        internalRoutes()

        get("/api/") {
            call.respondText("Hei!")
        }
    }
    routing {
    }
}
