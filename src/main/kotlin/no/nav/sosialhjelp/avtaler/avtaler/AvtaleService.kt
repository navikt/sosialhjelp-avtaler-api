package no.nav.sosialhjelp.avtaler.avtaler

import mu.KotlinLogging
import no.nav.sosialhjelp.avtaler.altinn.AltinnService
import no.nav.sosialhjelp.avtaler.altinn.Avgiver
import no.nav.sosialhjelp.avtaler.db.DatabaseContext
import no.nav.sosialhjelp.avtaler.db.transaction
import no.nav.sosialhjelp.avtaler.digipost.DigipostService
import no.nav.sosialhjelp.avtaler.kommune.AvtaleResponse

private val log = KotlinLogging.logger { }
private val sikkerLog = KotlinLogging.logger("tjenestekall")

class AvtaleService(
    private val altinnService: AltinnService,
    private val digipostService: DigipostService,
    val databaseContext: DatabaseContext,
) {

    suspend fun hentAvtaler(fnr: String, tjeneste: Avgiver.Tjeneste, token: String?): List<AvtaleResponse> {
        val avgivereFiltrert = altinnService.hentAvgivere(fnr = fnr, tjeneste = tjeneste, token = token)

        sikkerLog.info {
            "Filtrert avgivere for fnr: $fnr, tjeneste: $tjeneste, avgivere: $avgivereFiltrert"
        }

        val avtaler = transaction(databaseContext) { ctx ->
            ctx.avtaleStore.hentAvtalerForOrganisasjoner(avgivereFiltrert.map { it.orgnr }).associateBy {
                it.orgnr
            }
        }

        return avgivereFiltrert
            .map {
                AvtaleResponse(
                    orgnr = it.orgnr,
                    navn = it.navn,
                    avtaleversjon = avtaler[it.orgnr]?.avtaleversjon,
                    opprettet = avtaler[it.orgnr]?.opprettet,
                )
            }
    }

    suspend fun hentAvtale(
        fnr: String,
        orgnr: String,
        tjeneste: Avgiver.Tjeneste,
        token: String?
    ): AvtaleResponse? = hentAvtaler(fnr = fnr, tjeneste = tjeneste, token = token).associateBy {
        it.orgnr
    }[orgnr]

    suspend fun opprettAvtale(fnr: String, avtaleRequest: AvtaleRequest, navnInnsender: String): Avtale {
        log.info("Oppretter avtale for ${avtaleRequest.orgnr}")

        val avtale = Avtale(
            orgnr = avtaleRequest.orgnr,
            avtaleversjon = "1.0",
            navn_innsender = navnInnsender
        )
        digipostService.sendTilSignering(fnr, avtale)

        return avtale
    }

    suspend fun lagreAvtalestatus(navnInnsender: String, avtaleRequest: AvtaleRequest, status: SigneringsstatusRequest) {
        if (status.equals("SIGNERT")) {
            val avtale = transaction(databaseContext) { ctx ->
                ctx.avtaleStore.lagreAvtale(
                    Avtale(
                        orgnr = avtaleRequest.orgnr,
                        avtaleversjon = "1.0",
                        navn_innsender = navnInnsender
                    )
                )
            }
        }
    }
}