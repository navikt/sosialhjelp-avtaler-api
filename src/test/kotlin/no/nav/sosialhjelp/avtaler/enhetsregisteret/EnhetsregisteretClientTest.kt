package no.nav.sosialhjelp.avtaler.enhetsregisteret

import com.fasterxml.jackson.databind.DeserializationFeature
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import no.nav.sosialhjelp.avtaler.Configuration
import kotlin.test.Test

internal class EnhetsregisteretClientTest {
    private val mockClient = HttpClient(
        MockEngine { request ->
            respond(
                content = getJson(request.url),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
    ) {
        install(ContentNegotiation) {
            jackson {
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            }
        }
    }
    private val client = EnhetsregisteretClient(Configuration.enhetsregistertetProperties, mockClient)

    private fun getJson(url: Url): ByteArray {
        if (url.pathSegments.contains("123456789")) {
            return this::class.java.getResource("/enhetsregisteret/aksjeselskap.json")!!.openStream().readAllBytes()
        }
        return this::class.java.getResource("/enhetsregisteret/kommune.json")!!.openStream().readAllBytes()
    }

    @Test
    internal fun `Henter kommune fra enhetsregisteret`() = runBlocking(Dispatchers.IO) {
        val orgnr = "958935420" // Oslo kommune
        val enhet = client.hentOrganisasjonsenhet(orgnr)

        enhet?.organisasjonsnummer shouldBe orgnr
        enhet?.organisasjonsform?.erKommune() shouldBe true
    }

    @Test
    internal fun `Enhet fra enhetsregisteret som ikke er kommune får false på erKommune`() = runBlocking(Dispatchers.IO) {
        val orgnr = "123456789" // Aksjeselskap
        val enhet = client.hentOrganisasjonsenhet(orgnr)

        enhet?.organisasjonsnummer shouldBe orgnr
        enhet?.organisasjonsform?.erKommune() shouldBe false
    }
}
