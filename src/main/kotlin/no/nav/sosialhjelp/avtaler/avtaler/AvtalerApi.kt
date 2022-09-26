package no.nav.sosialhjelp.avtaler.avtaler

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

data class Person(
    val navn: String
)

fun Route.avtaleApi(avtaleService: AvtaleService) {
    route("/avtale") {
        get {
            val person = call.receive<Person>()
            val avtale = avtaleService.hentAvtale(person)
            call.respond(HttpStatusCode.OK, avtale)
        }
    }
}
