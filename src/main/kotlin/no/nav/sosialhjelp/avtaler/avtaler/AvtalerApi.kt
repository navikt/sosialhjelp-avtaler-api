package no.nav.sosialhjelp.avtaler.avtaler

import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.sosialhjelp.avtaler.altinn.Avgiver
import no.nav.sosialhjelp.avtaler.extractFnr

data class AvtaleRequest(val orgnr: String)

fun Route.avtaleApi(avtaleService: AvtaleService) {
    route("/avtale") {
        get("/{kommunenr}") {
            val kommunenummer = call.parameters["kommunenummer"] ?: "0000"
            val avtale = avtaleService.hentAvtale(
                kommunenummer,
                fnr = call.extractFnr(),
                tjeneste = Avgiver.Tjeneste.AVTALESIGNERING,
                token = this.context.getAccessToken()
            )
            call.respond(HttpStatusCode.OK, avtale)
        }

        post {
            val orgnr = call.receive<AvtaleRequest>()
            val avtale = avtaleService.opprettAvtale(orgnr, call.extractFnr(), token = this.context.getAccessToken())
            call.respond(HttpStatusCode.Created, avtale)
        }
    }
}

private fun ApplicationCall.getAccessToken(): String? {
    val authorizationHeader = request.parseAuthorizationHeader()
    if (authorizationHeader is HttpAuthHeader.Single && authorizationHeader.authScheme == "Bearer") {
        return authorizationHeader.blob
    }
    return null
}
