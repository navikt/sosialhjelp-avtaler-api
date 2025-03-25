package no.nav.sosialhjelp.avtaler.altinn

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.statement.request
import io.ktor.http.HttpStatusCode
import mu.KotlinLogging
import no.nav.sosialhjelp.avtaler.Configuration
import no.nav.sosialhjelp.avtaler.auth.Oauth2Client
import no.nav.sosialhjelp.avtaler.defaultHttpClientWithJsonHeaders

private val log = KotlinLogging.logger { }
private val sikkerLog = KotlinLogging.logger("tjenestekall")

interface AltinnClient {
    suspend fun hentTilganger(
        fnr: String,
        token: String?,
    ): AltinnTilgangerResponse?
}

class AltinnClientLocal : AltinnClient {
    override suspend fun hentTilganger(
        fnr: String,
        token: String?,
    ): AltinnTilgangerResponse =
        AltinnTilgangerResponse(
            false,
            listOf(AltinnTilgang("123456789", setOf(), setOf(), emptyList(), "Whatever kommune", "KOMM")),
            emptyMap(),
            mapOf("nav_sosialtjenester_digisos-avtale" to setOf("12345789")),
        )
}

class AltinnClientImpl(
    props: Configuration.AltinnProperties,
    private val tokenClient: Oauth2Client,
) : AltinnClient {
    private val client: HttpClient = defaultHttpClientWithJsonHeaders()
    private val baseUrl = props.baseUrl
    private val altinnRettigheterAudience = props.altinnRettigheterAudience

    override suspend fun hentTilganger(
        fnr: String,
        token: String?,
    ): AltinnTilgangerResponse? {
        if (token == null) {
            log.warn("Ingen access token i request, kan ikke hente ny token til altinn-proxy")
            return null
        }

        val scopedAccessToken = tokenClient.exchangeToken(token, altinnRettigheterAudience).accessToken

        val response =
            client.post("$baseUrl/altinn-tilganger") {
                bearerAuth(scopedAccessToken)
            }
        sikkerLog.info { "Hentet tilganger med url: ${response.request.url}" }
        if (response.status == HttpStatusCode.OK) {
            return response.body()
        }
        log.warn { "Kunne ikke hente tilganger, status: ${response.status}" }
        return null
    }
}
