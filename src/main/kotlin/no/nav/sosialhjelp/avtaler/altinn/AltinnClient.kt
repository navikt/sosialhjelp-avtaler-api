package no.nav.sosialhjelp.avtaler.altinn

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import mu.KotlinLogging
import no.nav.sosialhjelp.avtaler.Configuration
import no.nav.sosialhjelp.avtaler.auth.Oauth2Client

private val log = KotlinLogging.logger { }
private val sikkerLog = KotlinLogging.logger("tjenestekall")

class AltinnClient(props: Configuration.AltinnProperties, private val tokenClient: Oauth2Client) {

    private val client: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson {
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            }
        }
        defaultRequest {
            headers {
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
            }
        }
    }
    private val baseUrl = props.baseUrl
    private val altinnRettigheterAudience = props.altinnRettigheterAudience
    private val tokenXEndpointUrl = props.tokenXTokenEndpoint
    private val apiKey = props.apiKey

    suspend fun hentAvgivere(fnr: String, tjeneste: Avgiver.Tjeneste, token: String?): List<Avgiver> {
        token?.let {
            val scopedAccessToken = tokenClient.exchangeToken(token, tokenXEndpointUrl, altinnRettigheterAudience).accessToken

            val response = client.get("$baseUrl/api/serviceowner/reportees") {
                url {
                    parameters.append("ForceEIAuthentication", "")
                    parameters.append("subject", fnr)
                    parameters.append("serviceCode", tjeneste.kode)
                    parameters.append("serviceEdition", tjeneste.versjon.toString())
                    parameters.append("\$filter", "Type ne 'Person' and Status eq 'Active'")
                    parameters.append("\$top", "200")
                    header(HttpHeaders.Authorization, "Bearer $scopedAccessToken")
                }
            }
            sikkerLog.info { "Hentet avgivere med url: ${response.request.url}" }
            if (response.status == HttpStatusCode.OK) {
                return response.body() ?: emptyList()
            }
            log.warn { "Kunne ikke hente avgivere, status: ${response.status}" }
            return emptyList()
        }
        log.warn("Ingen access token i request, kan ikke hente ny token til altinn-proxy")
        return emptyList()
    }

    suspend fun hentRettigheter(fnr: String, orgnr: String, token: String?): Set<Avgiver.Tjeneste> {
        token?.let {
            val scopedAccessToken = tokenClient.exchangeToken(token, tokenXEndpointUrl, altinnRettigheterAudience).accessToken

            val response = client.get("$baseUrl/api/serviceowner/authorization/rights") {
                url {
                    parameters.append("ForceEIAuthentication", "")
                    parameters.append("subject", fnr)
                    parameters.append("reportee", orgnr)
                    // parameters.append("\$filter", Avgiver.Tjeneste.FILTER)
                    header(HttpHeaders.Authorization, "Bearer $scopedAccessToken")
                }
            }

            sikkerLog.info { "Hentet rettigheter med url: ${response.request.url}" }
            if (response.status == HttpStatusCode.OK) {
                return response.body<HentRettigheterResponse?>()?.tilSet() ?: emptySet()
            }
            log.warn { "Kunne ikke hente rettigheter, status: ${response.status}" }
            return emptySet()
        }
        log.warn("Ingen access token i request, kan ikke hente ny token til altinn-proxy")
        return emptySet()
    }

    private data class Rettighet(
        @JsonProperty("ServiceCode") val kode: String,
        @JsonProperty("ServiceEditionCode") val versjon: Int,
    )

    private data class HentRettigheterResponse(@JsonProperty("Rights") val rettigheter: List<Rettighet>) {
        fun tilSet(): Set<Avgiver.Tjeneste> = rettigheter.mapNotNull {
            Avgiver.Tjeneste.fra(it.kode, it.versjon)
        }.toSet()
    }
}
