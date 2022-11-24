package no.nav.sosialhjelp.avtaler.enhetsregisteret

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import no.nav.sosialhjelp.avtaler.Configuration
import kotlin.test.Test

internal class EnhetsregisteretClientTest {
    private val client = EnhetsregisteretClient(Configuration.enhetsregistertetProperties)

    @Test
    internal fun `Henter kommune fra enhetsregisteret`() = runBlocking(Dispatchers.IO) {
        val orgnr = "958935420" // Oslo kommune
        val enhet = client.hentOrganisasjonsenhet(orgnr)

        enhet?.organisasjonsnummer shouldBe orgnr
        enhet?.organisasjonsform?.erKommune() shouldBe true
    }

    @Test
    internal fun `Enhet fra enhetsregisteret som ikke er kommune får false på erKommune`() = runBlocking(Dispatchers.IO) {
        val orgnr = "929464958" // Aksjeselskap
        val enhet = client.hentOrganisasjonsenhet(orgnr)

        enhet?.organisasjonsnummer shouldBe orgnr
        enhet?.organisasjonsform?.erKommune() shouldBe false
    }
}
