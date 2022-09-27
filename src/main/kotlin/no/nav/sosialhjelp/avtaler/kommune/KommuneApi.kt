package no.nav.sosialhjelp.avtaler.kommune

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.kommuneApi() {
    route("/kommuner") {
        get {
            // val fnr = call.extractFnr()

            // altinnService.hentKommunerFor(fnr)
            call.respond(listOf(Kommune("0000", "Test kommune")))
        }
    }
}
