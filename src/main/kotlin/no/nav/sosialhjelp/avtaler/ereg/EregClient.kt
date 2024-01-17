package no.nav.sosialhjelp.avtaler.ereg

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import no.nav.sosialhjelp.avtaler.Configuration
import no.nav.sosialhjelp.avtaler.defaultHttpClientWithJsonHeaders

class EregClient(props: Configuration.EregProperties) {
    private val client: HttpClient = defaultHttpClientWithJsonHeaders()
    private val baseUrl = props.baseUrl

    suspend fun hentEnhetNavn(orgnr: String): String {
        return client.get("$baseUrl/v2/organisasjon/$orgnr").body<EnhetResponse>().navn.navnelinje1
    }
}

data class EnhetResponse(val navn: NavnResponse)

data class NavnResponse(val navnelinje1: String)
