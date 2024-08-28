package no.nav.sosialhjelp.avtaler.avtalemaler

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.sosialhjelp.avtaler.avtaler.AvtaleService
import no.nav.sosialhjelp.avtaler.gotenberg.GotenbergClient
import org.koin.ktor.ext.inject
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class AvtalemalMetadata(
    val name: String,
    val replacementMap: Map<String, String> = emptyMap(),
    val ingress: String? = null,
    val kvitteringstekst: String? = null,
    val ingressNynorsk: String? = null,
    val kvitteringstekstNynorsk: String? = null,
)

private val objectMapper = ObjectMapper().registerKotlinModule()

data class AvtalemalDto(
    val uuid: UUID,
    val navn: String,
    val publisert: OffsetDateTime?,
    val publishedTo: List<String> = emptyList(),
    val dokumentUrl: String = "/sosialhjelp/avtaler-api/api/avtalemal/$uuid/dokument",
    val previewUrl: String = "/sosialhjelp/avtaler-api/api/avtalemal/$uuid/preview",
    val replacementMap: Map<String, String> = emptyMap(),
)

fun Route.avtalemalerApi() {
    val avtalemalerService by inject<AvtalemalerService>()
    val injectionService by inject<InjectionService>()
    val gotenbergClient by inject<GotenbergClient>()
    val avtaleService by inject<AvtaleService>()

    route("/avtalemal") {
        get {
            val avtalemaler = avtalemalerService.hentAvtalemaler()
            val avtaler = avtaleService.hentAvtalemalToOrgnrMap()
            call.respond(HttpStatusCode.OK, avtalemaler.map { it.toDto(avtaler[it.uuid]) })
        }
        post {
            val multipart = call.receiveMultipart()
            val avtale = Avtalemal(uuid = UUID.randomUUID())

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        when (part.name) {
                            "file" -> {
                                avtale.mal = part.streamProvider().readAllBytes()
                            }

                            else -> "Ukjent filnavn: ${part.name}"
                        }
                    }

                    is PartData.FormItem -> {
                        if (part.name == "metadata") {
                            val metadata = objectMapper.readValue<AvtalemalMetadata>(part.value)
                            avtale.navn = metadata.name
                            avtale.replacementMap = metadata.replacementMap.mapValues { Replacement.valueOf(it.value) }
                            avtale.ingress = metadata.ingress
                            avtale.ingressNynorsk = metadata.ingressNynorsk
                            avtale.kvitteringstekst = metadata.kvitteringstekst
                            avtale.kvitteringstekstNynorsk = metadata.kvitteringstekstNynorsk
                        }
                    }

                    else -> {
                        log.warn { "Ukjent part: ${part.name}" }
                    }
                }
                part.dispose()
            }
            if (!avtale.isInitialized()) {
                call.respond(HttpStatusCode.BadRequest, "Mangler dokument og/eller navn")
                return@post
            }
            avtalemalerService.lagreAvtalemal(avtale).let {
                call.respond(HttpStatusCode.Created)
            }
        }
        route("/{uuid}") {
            delete {
                val uuid = call.uuid()
                avtalemalerService.slettAvtalemal(uuid)
                call.respond(HttpStatusCode.OK)
            }

            get("/dokument") {
                val uuid = call.uuid()
                val avtale = avtalemalerService.hentAvtalemal(uuid)
                if (avtale?.mal == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment
                        .withParameter(ContentDisposition.Parameters.FileName, "${avtale.navn}.docx")
                        .toString(),
                )
                call.respondBytes(avtale.mal, ContentType.Application.Docx)
            }

            route("/publiser") {
                post {
                    val uuid = call.uuid()
                    val kommuner = call.receiveNullable<List<String>>()
                    log.info { "Publiserer avtalemal $uuid til kommuner $kommuner" }
                    avtalemalerService.publiser(uuid, kommuner)
                    call.respond(HttpStatusCode.Created)
                }

                get("/status") {
                    val uuid = call.uuid()
                    val publiseringer = avtalemalerService.hentPubliseringer(uuid)
                    call.respond(HttpStatusCode.OK, publiseringer)
                }
            }

            get("/preview") {
                val uuid = call.uuid()
                val avtalemal = avtalemalerService.hentAvtalemal(uuid)
                if (avtalemal == null) {
                    call.respond(HttpStatusCode.NotFound, "{ \"message\": \"Fant ingen opplastet dokument på avtalemal med uuid $uuid\" }")
                    return@get
                }
                val replacements =
                    avtalemal.replacementMap.mapValues {
                        when (it.value) {
                            Replacement.KOMMUNENAVN -> "Oslo kommune"
                            Replacement.KOMMUNEORGNR -> "12345789"
                            Replacement.DATO -> LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                        }
                    }

                val converted =
                    ByteArrayOutputStream().use {
                        injectionService.injectReplacements(replacements, avtalemal.mal.inputStream(), it)

                        gotenbergClient.convertToPdf("${avtalemal.navn}_preview.docx", it.toByteArray())
                    }

                if (converted == null) {
                    log.error { "Klarte ikke å konvertere dokumentet til PDF" }
                    call.respond(HttpStatusCode.InternalServerError, "{ \"message\": \"Kunne ikke konvertere dokumentet til PDF\" }")
                    return@get
                }

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment
                        .withParameter(ContentDisposition.Parameters.FileName, "${avtalemal.navn}_preview.pdf")
                        .toString(),
                )
                call.respondBytes(converted, ContentType.Application.Pdf, HttpStatusCode.OK)
            }
        }
    }
}

private fun ApplicationCall.uuid(): UUID =
    requireNotNull(parameters["uuid"]?.let { UUID.fromString(it) }) {
        "Mangler uuid i URL"
    }

fun Avtalemal.toDto(publishedOrgnrs: List<String>?) =
    AvtalemalDto(
        uuid,
        navn,
        publisert,
        replacementMap =
            replacementMap.mapValues {
                it.value.name
            },
        publishedTo = publishedOrgnrs ?: emptyList(),
    )
