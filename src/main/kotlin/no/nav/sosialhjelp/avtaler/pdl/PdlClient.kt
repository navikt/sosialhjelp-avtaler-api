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
import no.nav.sosialhjelp.avtaler.graphql.GraphQLRequest
import no.nav.sosialhjelp.avtaler.pdl.api.HentPersonResponse
import no.nav.sosialhjelp.avtaler.pdl.api.HentPersonVariables
import no.nav.sosialhjelp.avtaler.pdl.api.PdlHentPerson

private val log = KotlinLogging.logger { }

private const val HEADER_BEHANDLINGSNUMMER = "behandlingsnummer"
private const val BEHANDLINGSNUMMER_AVTALER = "B563"

class PdlClient(
    props: Configuration.PdlProperties,
    private val tokenClient: Oauth2Client,
) {
    private val client: HttpClient = defaultHttpClientWithJsonHeaders()
    private val pdlUrl = props.pdlUrl
    private val pdlAudience = props.pdlAudience

    suspend fun hentPerson(
        ident: String,
        token: String,
    ): PdlHentPerson? {
        val query = getHentPersonQuery()
        val request =
            GraphQLRequest(
                query = query,
                variables =
                    HentPersonVariables(
                        ident = ident,
                    ),
            )

        val scopedAccessToken =
            tokenClient.exchangeToken(token, pdlAudience).access_token ?: error(
                "Got no token from tokenX",
            )

        val response =
            client.post(pdlUrl) {
                setBody(request)
                header(HttpHeaders.Authorization, "Bearer $scopedAccessToken")
                header(HEADER_BEHANDLINGSNUMMER, BEHANDLINGSNUMMER_AVTALER)
            }

        if (response.status == HttpStatusCode.OK) {
            val hentPersonResponse = response.body<HentPersonResponse>()
            return if (!hentPersonResponse.errors.isNullOrEmpty()) {
                hentPersonResponse.errors.forEach {
                    log.error("Pdl - hentPerson feilet: ${it.errorMessage()}")
                }
                null
            } else {
                hentPersonResponse.data
            }
        } else {
            log.error("Pdl - hentPerson feilet. url: $pdlUrl, feil: ${response.status.value}")
            return null
        }
    }

    private fun getHentPersonQuery(): String =
        this::class.java
            .getResource("/pdl/hentPerson.graphql")!!
            .readText()
            .replace("[\n\r]", "")
}
