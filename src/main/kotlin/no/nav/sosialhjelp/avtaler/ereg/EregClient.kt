package no.nav.sosialhjelp.avtaler.ereg

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import no.nav.sosialhjelp.avtaler.Configuration
import no.nav.sosialhjelp.avtaler.defaultHttpClientWithJsonHeaders

class EregClient(val props: Configuration.EregProperties) {
    private val client: HttpClient = defaultHttpClientWithJsonHeaders()
    private val baseUrl = props.baseUrl

    suspend fun hentEnhet(orgnr: String): Enhet {
        return client.get("$baseUrl/v2/organisasjon/$orgnr") {
            bearerAuth("abc")
        }.body()
    }
}

data class Enhet(val navn: String, val orgnr: String, val kommunenummer: String)
