package no.nav.sosialhjelp.avtaler.enhetsregisteret

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.sosialhjelp.avtaler.Configuration

private val log = KotlinLogging.logger { }

class EnhetsregisteretClient(props: Configuration.EnhetsregistertetProperties, httpClient: HttpClient) {
    private val baseUrl = props.baseUrl
    private val client = httpClient

    suspend fun hentOrganisasjonsenhet(orgnr: String): Organisasjonsenhet? =
        hentEnhetHelper("$baseUrl/enheter/$orgnr")

    private suspend fun hentEnhetHelper(url: String): Organisasjonsenhet? {
        try {
            log.info { "Henter enhet med url: $url" }
            return withContext(Dispatchers.IO) {
                val response = client.get(url)
                when (response.status) {
                    HttpStatusCode.OK -> response.body()
                    HttpStatusCode.NotFound -> null
                    else -> throw EnhetsregisteretClientException("Uventet svar fra tjeneste: ${response.status}", null)
                }
            }
        } catch (e: ResponseException) {
            throw EnhetsregisteretClientException("Feil under henting av organisasjonsenhet", e)
        }
    }
}
