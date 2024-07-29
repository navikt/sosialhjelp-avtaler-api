package no.nav.sosialhjelp.avtaler.kommune

import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import mu.KotlinLogging
import no.nav.sosialhjelp.avtaler.AZURE_AUTH
import no.nav.sosialhjelp.avtaler.Configuration
import no.nav.sosialhjelp.avtaler.TOKEN_X_AUTH
import no.nav.sosialhjelp.avtaler.ereg.EregClient
import org.koin.ktor.ext.inject

private val log = KotlinLogging.logger { }

fun Route.kommuneApi() {
    val kommuneService by inject<KommuneService>()
    val eregService by inject<EregClient>()

    route("/kommuner") {
        authenticate(if (Configuration.local) "local" else TOKEN_X_AUTH) {
            get {
                log.info("Henter alle kommuner")
                val kommuner = kommuneService.getAlleKommuner()
                call.respond(kommuner)
            }
        }
        authenticate(if (Configuration.local) "local" else AZURE_AUTH) {
            get("/{orgnr}") {
                val orgnr = call.parameters["orgnr"] ?: error("Mangler orgnr")
                log.info("Henter kommune med orgnr $orgnr")
                val kommunenavn = eregService.hentEnhetNavn(orgnr)
                call.respond(KommunenavnResponse(kommunenavn))
            }
        }
    }
}

data class KommunenavnResponse(
    val kommunenavn: String,
)
