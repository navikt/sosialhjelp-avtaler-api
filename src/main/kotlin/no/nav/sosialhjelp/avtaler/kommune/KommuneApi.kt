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
import no.nav.sosialhjelp.avtaler.altinn.Avgiver
import no.nav.sosialhjelp.avtaler.avtaler.AvtaleService
import no.nav.sosialhjelp.avtaler.extractFnr
import org.koin.ktor.ext.inject

private val log = KotlinLogging.logger { }

fun Route.kommuneApi() {
    val avtaleService by inject<AvtaleService>()

    route("/kommuner") {
        get {
            val kommuner =
                avtaleService.hentKommuner(
                    fnr = call.extractFnr(),
                    tjeneste = Avgiver.Tjeneste.AVTALESIGNERING,
                    this.context.getAccessToken(),
                )
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
