package no.nav

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.plugins.configureRouting

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configureRouting()
    }.start(wait = true)
}
