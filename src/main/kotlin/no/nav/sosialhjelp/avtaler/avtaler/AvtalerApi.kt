package no.nav.sosialhjelp.avtaler.avtaler

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.avtaleApi(avtaleService: AvtaleService) {
    route("/avtale") {
        get {
            val avtale = avtaleService.hentAvtale()
            call.respond(HttpStatusCode.OK, avtale)
        }
    }
}
