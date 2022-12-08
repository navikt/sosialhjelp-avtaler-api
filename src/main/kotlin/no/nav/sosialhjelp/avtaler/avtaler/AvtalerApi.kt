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
import no.nav.sosialhjelp.avtaler.pdl.PersonNavnService

data class AvtaleRequest(val orgnr: String)

data class SigneringsstatusRequest(val orgnr: String, val status: String, val token: String)

fun Route.avtaleApi(avtaleService: AvtaleService, personNavnService: PersonNavnService) {
    route("/avtale") {
        get("/{kommunenr}") {
            val kommunenummer = call.kommunenr()
            val avtale = avtaleService.hentAvtale(
                fnr = call.extractFnr(),
                orgnr = kommunenummer,
                tjeneste = Avgiver.Tjeneste.AVTALESIGNERING,
                token = this.context.getAccessToken()
            )
            if (avtale == null) {
                call.response.status(HttpStatusCode.NotFound)
                return@get
            }
            call.respond(HttpStatusCode.OK, avtale)
        }

        post("/signer") {
            val avtaleRequest = call.receive<AvtaleRequest>()
            val fnr = call.extractFnr()
            val token = this.context.getAccessToken() ?: throw RuntimeException("Kunne ikke hente access token")
            val navnInnsender = personNavnService.getFulltNavn(fnr, token)

            val signeringsurl = avtaleService.signerAvtale(fnr, avtaleRequest, navnInnsender)
            call.respond(HttpStatusCode.Created, signeringsurl)
        }

        post("/signeringsstatus") {
            // lagre singeringsstatus i database
            val signeringsstatusRequest = call.receive<SigneringsstatusRequest>()
            val fnr = call.extractFnr()
            val token = this.context.getAccessToken() ?: throw RuntimeException("Kunne ikke hente access token")
            val navnInnsender = personNavnService.getFulltNavn(fnr, token)

            avtaleService.sjekkAvtaleStatus(navnInnsender, signeringsstatusRequest.orgnr, signeringsstatusRequest.token)
            val avtaleResponse = avtaleService.hentAvtale(
                fnr = call.extractFnr(),
                orgnr = signeringsstatusRequest.orgnr,
                tjeneste = Avgiver.Tjeneste.AVTALESIGNERING,
                token = this.context.getAccessToken()
            )
            if (avtaleResponse == null) {
                call.response.status(HttpStatusCode.NotFound)
                return@post
            }
            call.respond(HttpStatusCode.OK, avtaleResponse)
        }

        post("/intern-test-signering") {
            val fnr = call.extractFnr()
            val token = this.context.getAccessToken() ?: throw RuntimeException("Kunne ikke hente access token")

            val signeringsurl = avtaleService.signerTestAvtale(fnr)
            call.respond(HttpStatusCode.Created, signeringsurl)
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

private fun ApplicationCall.kommunenr(): String = requireNotNull(parameters["kommunenr"]) {
    "Mangler kommunenr i URL"
}
