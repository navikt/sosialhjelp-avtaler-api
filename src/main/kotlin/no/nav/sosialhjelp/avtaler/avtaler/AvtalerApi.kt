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
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.sosialhjelp.avtaler.altinn.Avgiver
import no.nav.sosialhjelp.avtaler.avtalemaler.AvtalemalerService
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
    val avtalemalerService by inject<AvtalemalerService>()

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
                val avtalemal = avtale.avtalemal_uuid?.let { avtalemalerService.hentAvtalemal(it) }
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
                            ingress = avtalemal?.ingress,
                            ingressNynorsk = avtalemal?.ingressNynorsk,
                            kvitteringstekstNynorsk = avtalemal?.kvitteringstekstNynorsk,
                            kvitteringstekst = avtalemal?.kvitteringstekst,
                        )
                    },
                )
            }

            get("/eksempel") {
                val uuid = call.uuid()
                val avtale =
                    avtaleService.hentAvtale(
                        call.extractFnr(),
                        uuid,
                        Avgiver.Tjeneste.AVTALESIGNERING,
                        this.context.getAccessToken(),
                    )
                if (avtale?.avtalemal_uuid == null) {
                    return@get call.respond(HttpStatusCode.NotFound)
                }
                val eksempelDokument = avtalemalerService.hentEksempel(avtale.avtalemal_uuid)

                if (eksempelDokument == null) {
                    return@get call.response.status(HttpStatusCode.NotFound)
                }

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment
                        .withParameter(ContentDisposition.Parameters.FileName, "Eksempel på ${avtale.navn}.pdf")
                        .toString(),
                )
                call.respondBytes(eksempelDokument, ContentType.Application.Pdf)
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
                    return@get call.respond(HttpStatusCode.NotFound)
                }
                val avtaleDokument =
                    avtaleService.hentAvtaleDokument(
                        call.extractFnr(),
                        uuid,
                        Avgiver.Tjeneste.AVTALESIGNERING,
                        this.context.getAccessToken(),
                    )

                if (avtaleDokument == null) {
                    return@get call.response.status(HttpStatusCode.NotFound)
                }

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment
                        .withParameter(ContentDisposition.Parameters.FileName, "${avtale.navn}.pdf")
                        .toString(),
                )
                call.respondBytes(avtaleDokument, ContentType.Application.Pdf)
            }

            get("/signert-avtale") {
                val uuid = call.uuid()
                val fnr = call.extractFnr()
                val token = this.context.getAccessToken() ?: error("Kunne ikke hente access token")
                val (title, document) =
                    avtaleService.hentSignertAvtaleDokumentFraDatabaseEllerDigipost(
                        fnr,
                        Avgiver.Tjeneste.AVTALESIGNERING,
                        token,
                        uuid,
                    )

                if (document == null) {
                    call.response.status(HttpStatusCode.NotFound)
                    return@get
                }

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment
                        .withParameter(ContentDisposition.Parameters.FileName, "$title.pdf")
                        .toString(),
                )

                document.use { doc ->
                    call.respondOutputStream(contentType = ContentType.Application.Pdf, status = HttpStatusCode.OK) {
                        this.use { outputStream ->
                            doc.copyTo(outputStream)
                        }
                    }
                }
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
            val avtalemal = avtaleResponse?.avtalemal_uuid?.let { avtalemalerService.hentAvtalemal(it) }

            if (avtaleResponse == null) {
                return@post call.response.status(HttpStatusCode.NotFound)
            }
            call.respond(
                HttpStatusCode.OK,
                AvtaleResponse(
                    avtaleResponse.uuid,
                    avtaleResponse.orgnr,
                    avtaleResponse.navn,
                    avtaleResponse.navn_innsender,
                    avtaleResponse.avtaleversjon,
                    avtaleResponse.opprettet,
                    avtaleResponse.erSignert,
                    ingress = avtalemal?.ingress,
                    kvitteringstekst = avtalemal?.kvitteringstekst,
                    ingressNynorsk = avtalemal?.ingressNynorsk,
                    kvitteringstekstNynorsk = avtalemal?.kvitteringstekstNynorsk,
                ),
            )
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
