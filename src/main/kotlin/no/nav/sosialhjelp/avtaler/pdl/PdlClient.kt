package no.nav.sosialhjelp.avtaler.pdl

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import mu.KotlinLogging
import no.nav.sosialhjelp.avtaler.Configuration
import no.nav.sosialhjelp.avtaler.auth.Oauth2Client
import no.nav.sosialhjelp.avtaler.defaultHttpClientWithJsonHeaders
import no.nav.sosialhjelp.avtaler.pdl.api.HentPersonRequest
import no.nav.sosialhjelp.avtaler.pdl.api.HentPersonResponse
import no.nav.sosialhjelp.avtaler.pdl.api.PdlHentPerson
import no.nav.sosialhjelp.avtaler.pdl.api.Variables

private val log = KotlinLogging.logger { }
private val sikkerLog = KotlinLogging.logger("tjenestekall")

class PdlClient(
    props: Configuration.PdlProperties,
    private val tokenClient: Oauth2Client
) {

    private val client: HttpClient = defaultHttpClientWithJsonHeaders()
    private val pdlUrl = props.pdlUrl
    private val pdlAudience = props.pdlAudience

    suspend fun hentPerson(ident: String, token: String): PdlHentPerson? {
        val query = getHentPersonQuery()
        val request = HentPersonRequest(
            query = query,
            variables = Variables(
                ident = ident
            )
        )

        val scopedAccessToken = tokenClient.exchangeToken(token, pdlAudience).accessToken

        val response = client.post(pdlUrl) {
            setBody(request)
            header(HttpHeaders.Authorization, "Bearer $scopedAccessToken")
            header(HEADER_TEMA, TEMA_KOM)
            // call-id?
        }

        when (response.status) {
            HttpStatusCode.OK -> {
                val hentPersonResponse = response.body<HentPersonResponse>()
                return if (!hentPersonResponse.errors.isNullOrEmpty()) {
                    hentPersonResponse.errors.forEach {
                        log.error("Pdl - hentPerson feilet: ${it.errorMessage()}")
                    }
                    null
                } else {
                    hentPersonResponse.data
                }
            }
            else -> {
                log.error("Pdl - hentPerson feilet. url: $pdlUrl, feil: ${response.status.value}")
                return null
            }
        }
    }

    private fun getHentPersonQuery(): String {
        return this::class.java.getResource("/pdl/hentPerson.graphql")!!
            .readText()
            .replace("[\n\r]", "")
    }

    companion object {
        private const val HEADER_TEMA = "Tema"
        private const val TEMA_KOM = "KOM"
    }
}
