package no.nav.sosialhjelp.avtaler.avtaler

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

data class Kommune(
    val orgnr: String
)

fun Route.avtaleApi(avtaleService: AvtaleService) {
    route("/avtale") {
        get("/{kommunenr}") {
            val kommunenummer = call.parameters["kommunenummer"] ?: "0000"
            val avtale = avtaleService.hentAvtale(kommunenummer)
            call.respond(HttpStatusCode.OK, avtale)
        }

        post {
            val kommunenummer = call.receive<Kommune>()
            val avtale = avtaleService.opprettAvtale(kommunenummer)
            call.respond(HttpStatusCode.OK, avtale)
        }
    }
}
