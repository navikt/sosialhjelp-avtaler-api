package no.nav.sosialhjelp.avtaler.ereg

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.server.response.cacheControl
import kotlinx.coroutines.runBlocking
import no.nav.sosialhjelp.avtaler.Configuration
import no.nav.sosialhjelp.avtaler.kommune.KommuneService

interface EregClient {
    suspend fun hentEnhetNavn(orgnr: String): String
}

class EregClientImpl(
    private val client: HttpClient,
    props: Configuration.EregProperties,
) : EregClient {
    private val baseUrl = props.baseUrl

    override suspend fun hentEnhetNavn(orgnr: String): String =
        client
            .get("$baseUrl/v2/organisasjon/$orgnr") {
                headers {
                    accept(ContentType.Application.Json)
                    contentType(ContentType.Application.Json)
                    // One year
                    cacheControl(CacheControl.MaxAge(31_556_926))
                }
            }.body<EnhetResponse>()
            .navn
            ?.navnelinje1 ?: error("Fant ikke enhet med orgnr $orgnr")
}

class EregClientLocal(
    private val kommuneService: KommuneService,
) : EregClient {
    val kommuner by lazy { runBlocking { kommuneService.getAlleKommuner() }.associateBy { it.orgnr } }

    override suspend fun hentEnhetNavn(orgnr: String): String = kommuner[orgnr]?.navn ?: error("Fant ikke kommune med orgnr $orgnr")
}

data class EnhetResponse(
    val navn: NavnResponse?,
)

data class NavnResponse(
    val navnelinje1: String,
)
