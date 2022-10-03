package no.nav.sosialhjelp.avtaler.avtale

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.sosialhjelp.avtaler.altinn.AltinnService
import no.nav.sosialhjelp.avtaler.altinn.Avgiver
import no.nav.sosialhjelp.avtaler.avtaler.Avtale
import no.nav.sosialhjelp.avtaler.avtaler.AvtaleRequest
import no.nav.sosialhjelp.avtaler.avtaler.AvtaleService
import no.nav.sosialhjelp.avtaler.avtaler.avtaleApi
import no.nav.sosialhjelp.avtaler.kommune.Kommune
import no.nav.sosialhjelp.avtaler.test.TestRouting
import kotlin.test.BeforeTest
import kotlin.test.Test

internal class AvtaleApiTest {
    private val altinnService = mockk<AltinnService>()

    private val routing = TestRouting {
        authenticate("test") {
            avtaleApi(AvtaleService(altinnService))
        }
    }

    private val fnrInnsender = routing.principal.getFnr()
    private val avgiver = Avgiver(
        navn = "Brillesjø AS",
        orgnr = "456313701",
        parentOrgnr = null,
    )
    private val kommune = Kommune(
        orgnr = avgiver.orgnr,
        navn = "Dag Ledersen",
        opprettet = null,
    )
    private val opprettAvtale = Avtale(
        orgnr = avgiver.orgnr,
        navn = "Navn navnesen",
        avtaleversjon = "1.0",
        opprettet = null
    )
    private val opprettAvtale2 = AvtaleRequest(
        orgnr = avgiver.orgnr,
    )

    @BeforeTest
    internal fun setUp() {
        coEvery {
            altinnService.hentAvgivere(fnrInnsender, Avgiver.Tjeneste.AVTALESIGNERING)
        } returns listOf(avgiver)
    }
/*
    @Test
    internal fun `henter virksomheter med avtale`() = routing.test {
        val response = client.get("/kommuner")
        response.status shouldBe HttpStatusCode.OK
    }

    @Test
    internal fun `henter virksomhet med avtale`() = routing.test {
        val response = client.get("/avtale/${kommune.orgnr}")
        response.status shouldBe HttpStatusCode.OK
    }

    @Test
    internal fun `oppretter ny avtale`() = routing.test {
        harRettighetSignering(opprettAvtale.orgnr)
        val response = client.post("/avtale") {
            setBody(opprettAvtale)
        }
        response.status shouldBe HttpStatusCode.Created
    }

    @Test
    internal fun `oppretter ny avtale uten tilgang`() = routing.test {
        harIkkeRettighetSignering(opprettAvtale.orgnr)
        val response = client.post("/avtale") {
            setBody(opprettAvtale2)
        }
        response.status shouldBe HttpStatusCode.Forbidden
    }


 */
    private fun harRettighetSignering(orgnr: String) = coEvery {
        altinnService.harTilgangTilSignering(fnrInnsender, orgnr)
    } returns true

    private fun harIkkeRettighetSignering(orgnr: String) = coEvery {
        altinnService.harTilgangTilSignering(fnrInnsender, orgnr)
    } returns false
}
