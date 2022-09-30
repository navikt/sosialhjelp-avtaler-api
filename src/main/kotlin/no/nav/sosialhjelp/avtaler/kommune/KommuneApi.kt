package no.nav.sosialhjelp.avtaler.kommune

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.sosialhjelp.avtaler.altinn.AltinnService
import no.nav.sosialhjelp.avtaler.altinn.Avgiver
import no.nav.sosialhjelp.avtaler.avtaler.AvtaleService
import no.nav.sosialhjelp.avtaler.extractFnr

fun Route.kommuneApi(avtaleService: AvtaleService, altinnService: AltinnService) {
    route("/kommuner") {
        get {
            val fnr = call.extractFnr()

            val kommunerFraAltinn = altinnService.hentAvgivere(fnr, Avgiver.Tjeneste.AVTALESIGNERING)

            val kommuner = kommunerFraAltinn.map {
                val avtale = avtaleService.hentAvtale(it.orgnr, fnr, Avgiver.Tjeneste.AVTALESIGNERING)
                Kommune(orgnr = avtale.orgnr, navn = avtale.navn, opprettet = avtale.opprettet)
            }.toList()
            call.respond(HttpStatusCode.OK, kommuner)
        }
    }
}
