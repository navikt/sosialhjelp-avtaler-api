package no.nav.sosialhjelp.avtaler.kommune

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import mu.KotlinLogging
import org.koin.ktor.ext.inject

private val log = KotlinLogging.logger { }

fun Route.kommuneApi() {
    val kommuneService by inject<KommuneService>()

    route("/kommuner") {
        get {
            log.info("Henter alle kommuner")
            val kommuner = kommuneService.getAlleKommuner()
            call.respond(kommuner)
        }
    }
}
