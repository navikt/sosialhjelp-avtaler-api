package no.nav.sosialhjelp.avtaler.ereg

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import no.nav.sosialhjelp.avtaler.Configuration
import no.nav.sosialhjelp.avtaler.defaultHttpClientWithJsonHeaders
import no.nav.sosialhjelp.avtaler.kommune.KommuneService

interface EregClient {
    suspend fun hentEnhetNavn(orgnr: String): String
}

class EregClientImpl(
    props: Configuration.EregProperties,
) : EregClient {
    private val client: HttpClient = defaultHttpClientWithJsonHeaders()
    private val baseUrl = props.baseUrl

    override suspend fun hentEnhetNavn(orgnr: String): String =
        client
            .get("$baseUrl/v2/organisasjon/$orgnr")
            .body<EnhetResponse>()
            .navn.navnelinje1
}

class EregClientLocal(
    private val kommuneService: KommuneService,
) : EregClient {
    val kommuner by lazy { runBlocking { kommuneService.getAlleKommuner() }.associateBy { it.orgnr } }

    override suspend fun hentEnhetNavn(orgnr: String): String = kommuner[orgnr]?.navn ?: error("Fant ikke kommune med orgnr $orgnr")
}

data class EnhetResponse(
    val navn: NavnResponse,
)

data class NavnResponse(
    val navnelinje1: String,
)
