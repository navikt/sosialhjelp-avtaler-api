package no.nav.sosialhjelp.avtaler.kommune

import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import mu.KotlinLogging
import no.nav.sosialhjelp.avtaler.altinn.AltinnService
import no.nav.sosialhjelp.avtaler.altinn.Avgiver
import no.nav.sosialhjelp.avtaler.avtaler.AvtaleService
import no.nav.sosialhjelp.avtaler.extractFnr

private val log = KotlinLogging.logger { }

fun Route.kommuneApi(avtaleService: AvtaleService, altinnService: AltinnService) {
    route("/kommuner") {
        get {
            val fnr = call.extractFnr()

            val kommunerFraAltinn = altinnService.hentAvgivere(fnr, Avgiver.Tjeneste.AVTALESIGNERING, this.context.getAccessToken())

            val kommuner = kommunerFraAltinn.map {
                val avtale = avtaleService.hentAvtale(it.orgnr, fnr, Avgiver.Tjeneste.AVTALESIGNERING, this.context.getAccessToken())
                Kommune(orgnr = avtale.orgnr, navn = avtale.navn, opprettet = avtale.opprettet)
            }.toList()
            call.respond(HttpStatusCode.OK, kommuner)
        }
    }
}

private fun ApplicationCall.getAccessToken(): String? {
    val authorizationHeader = request.parseAuthorizationHeader()
    if (authorizationHeader is HttpAuthHeader.Single && authorizationHeader.authScheme == "Bearer") {
        log.info { "returning authheader" }

        return authorizationHeader.blob
    }
    return null
}
