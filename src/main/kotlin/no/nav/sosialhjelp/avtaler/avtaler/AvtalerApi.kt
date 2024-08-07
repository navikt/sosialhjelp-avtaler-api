package no.nav.sosialhjelp.avtaler.avtaler

import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.sosialhjelp.avtaler.altinn.Avgiver
import no.nav.sosialhjelp.avtaler.extractFnr
import no.nav.sosialhjelp.avtaler.kommune.AvtaleResponse
import org.koin.ktor.ext.inject
import java.util.UUID

data class SigneringsstatusRequest(
    val uuid: UUID,
    val token: String,
)

fun Route.avtaleApi() {
    val avtaleService by inject<AvtaleService>()

    route("/avtale") {
        get {
            val kommuner =
                avtaleService.hentKommuner(
                    fnr = call.extractFnr(),
                    tjeneste = Avgiver.Tjeneste.AVTALESIGNERING,
                    this.context.getAccessToken(),
                )
            call.respond(HttpStatusCode.OK, kommuner)
        }
        route("/{uuid}") {
            get {
                val uuid = call.uuid()
                val avtale =
                    avtaleService.hentAvtale(
                        fnr = call.extractFnr(),
                        uuid = uuid,
                        tjeneste = Avgiver.Tjeneste.AVTALESIGNERING,
                        token = this.context.getAccessToken(),
                    )
                if (avtale == null) {
                    call.response.status(HttpStatusCode.NotFound)
                    return@get
                }
                call.respond(
                    HttpStatusCode.OK,
                    avtale.let {
                        AvtaleResponse(
                            uuid = it.uuid,
                            avtaleversjon = it.avtaleversjon,
                            opprettet = it.opprettet,
                            navn = it.navn,
                            navnInnsender = it.navn_innsender,
                            orgnr = it.orgnr,
                        )
                    },
                )
            }

            get("/avtale") {
                val uuid = call.uuid()
                val avtale =
                    avtaleService.hentAvtale(
                        call.extractFnr(),
                        uuid,
                        Avgiver.Tjeneste.AVTALESIGNERING,
                        this.context.getAccessToken(),
                    )
                if (avtale == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                val avtaleDokument =
                    avtaleService.hentAvtaleDokument(
                        call.extractFnr(),
                        uuid,
                        Avgiver.Tjeneste.AVTALESIGNERING,
                        this.context.getAccessToken(),
                    )

                if (avtaleDokument == null) {
                    call.response.status(HttpStatusCode.NotFound)
                    return@get
                }

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment
                        .withParameter(ContentDisposition.Parameters.FileName, "${avtale.navn}.pdf")
                        .toString(),
                )
                call.respondBytes(avtaleDokument, ContentType.Application.Pdf)
            }
            post("/signer") {
                val uuid = call.uuid()
                val fnr = call.extractFnr()
                val token = this.context.getAccessToken() ?: throw RuntimeException("Kunne ikke hente access token")

                val signeringsurl = avtaleService.signerAvtale(fnr, uuid, token)
                if (signeringsurl == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@post
                }
                call.respond(HttpStatusCode.Created, signeringsurl)
            }
        }

        get("/signert-avtale/{uuid}") {
            val uuid = call.uuid()
            val signertAvtaleDokument = avtaleService.hentSignertAvtaleDokumentFraDatabaseEllerDigipost(uuid)

            if (signertAvtaleDokument == null) {
                call.response.status(HttpStatusCode.NotFound)
                return@get
            }

            signertAvtaleDokument.use { call.respond(HttpStatusCode.OK, it) }
        }

        post("/signeringsstatus") {
            val signeringsstatusRequest = call.receive<SigneringsstatusRequest>()
            val fnr = call.extractFnr()
            val token = this.context.getAccessToken() ?: throw RuntimeException("Kunne ikke hente access token")

            val avtaleResponse =
                avtaleService.sjekkAvtaleStatusOgLagreSignertDokument(
                    statusQueryToken = signeringsstatusRequest.token,
                    uuid = signeringsstatusRequest.uuid,
                    fnr = fnr,
                    token = token,
                )

            if (avtaleResponse == null) {
                call.response.status(HttpStatusCode.NotFound)
                return@post
            }
            call.respond(HttpStatusCode.OK, avtaleResponse)
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

private fun ApplicationCall.uuid(): UUID =
    requireNotNull(parameters["uuid"]?.let { UUID.fromString(it) }) {
        "Mangler uuid i URL"
    }
