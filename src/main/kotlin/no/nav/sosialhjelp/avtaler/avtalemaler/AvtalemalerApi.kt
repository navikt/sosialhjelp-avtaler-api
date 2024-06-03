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
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject
import java.time.OffsetDateTime
import java.util.UUID

data class AvtalemalMetadata(val name: String)

private val objectMapper = ObjectMapper().registerKotlinModule()

data class AvtalemalDto(
    val uuid: UUID,
    val navn: String,
    val dokumentUrl: String,
    val publisert: OffsetDateTime?,
)

fun Route.avtalemalerApi() {
    val avtalemalerService by inject<AvtalemalerService>()

    route("/avtalemal") {
        get {
            val avtaler = avtalemalerService.hentAvtalemaler()
            call.respond(HttpStatusCode.OK, avtaler.map { it.toDto() })
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

                            "metadata" -> {
                                avtale.navn = objectMapper.readValue<AvtalemalMetadata>(part.streamProvider()).name
                            }

                            else -> "Ukjent filnavn: ${part.name}"
                        }
                    }

                    is PartData.FormItem -> {
                        if (part.name == "metadata") {
                            val metadata = objectMapper.readValue<AvtalemalMetadata>(part.value)
                            avtale.navn = metadata.name
                        }
                    }

                    else -> {
                        println("Ukjent part: ${part.name}")
                    }
                }
                part.dispose()
            }

            avtalemalerService.lagreAvtalemal(avtale).let {
                call.respond(HttpStatusCode.Created, it.toDto())
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
                    ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "${avtale.navn}.pdf")
                        .toString(),
                )
                call.respondBytes(avtale.mal!!, ContentType.Application.Pdf)
            }

            post("/publiser") {
                val uuid = call.uuid()
                avtalemalerService.publiser(uuid)
            }
        }
    }
}

private fun ApplicationCall.uuid(): UUID =
    requireNotNull(parameters["uuid"]?.let { UUID.fromString(it) }) {
        "Mangler uuid i URL"
    }

fun Avtalemal.toDto() = AvtalemalDto(uuid, requireNotNull(navn), "/sosialhjelp/avtaler-api/api/avtalemal/$uuid/dokument", publisert)
