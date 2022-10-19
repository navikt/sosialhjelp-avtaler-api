package no.nav.sosialhjelp.avtaler.avtaler

import mu.KotlinLogging
import no.nav.sosialhjelp.avtaler.altinn.AltinnService
import no.nav.sosialhjelp.avtaler.altinn.Avgiver
import no.nav.sosialhjelp.avtaler.db.DatabaseContext
import no.nav.sosialhjelp.avtaler.db.transaction
import no.nav.sosialhjelp.avtaler.kommune.Kommune

private val log = KotlinLogging.logger { }
private val sikkerLog = KotlinLogging.logger("tjenestekall")

class AvtaleService(
    private val altinnService: AltinnService,
    val databaseContext: DatabaseContext,
) {

    suspend fun hentAvtaler(fnr: String, tjeneste: Avgiver.Tjeneste, token: String?): List<Kommune> {
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
                Kommune(
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
    ): Kommune? = hentAvtaler(fnr = fnr, tjeneste = tjeneste, token = token).associateBy {
        it.orgnr
    }[orgnr]

    suspend fun opprettAvtale(avtale: AvtaleRequest, fnrInnsender: String, token: String?): Avtale {
/*
        if (!altinnService.harTilgangTilSignering(fnrInnsender, avtale.orgnr)) {
            throw AvtaleManglerTilgangException(avtale.orgnr)
        }

 */
        log.info("Oppretter avtale for ${avtale.orgnr}")

        val avtale = transaction(databaseContext) { ctx ->
            ctx.avtaleStore.lagreAvtale(
                Avtale(
                    orgnr = avtale.orgnr,
                    avtaleversjon = null
                )
            )
        }

        return avtale
    }
}
