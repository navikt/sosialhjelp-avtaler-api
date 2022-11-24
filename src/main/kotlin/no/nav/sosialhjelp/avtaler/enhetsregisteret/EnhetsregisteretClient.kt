package no.nav.sosialhjelp.avtaler.enhetsregisteret

import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.sosialhjelp.avtaler.Configuration

private val log = KotlinLogging.logger { }

class EnhetsregisteretClient(props: Configuration.EnhetsregistertetProperties) {
    private val baseUrl = props.baseUrl
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson {
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            }
        }
    }

    suspend fun hentOrganisasjonsenhet(orgnr: String): Kommune? =
        hentEnhetHelper("$baseUrl/enheter/$orgnr")

    private suspend fun hentEnhetHelper(url: String): Kommune? {
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
