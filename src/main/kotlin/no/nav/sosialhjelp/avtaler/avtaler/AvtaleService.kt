package no.nav.sosialhjelp.avtaler.avtaler

import mu.KotlinLogging
import no.nav.sosialhjelp.avtaler.altinn.AltinnService
import no.nav.sosialhjelp.avtaler.altinn.Avgiver
import no.nav.sosialhjelp.avtaler.db.DatabaseContext
import no.nav.sosialhjelp.avtaler.db.transaction
import no.nav.sosialhjelp.avtaler.digipost.DigipostJobbData
import no.nav.sosialhjelp.avtaler.digipost.DigipostResponse
import no.nav.sosialhjelp.avtaler.digipost.DigipostService
import no.nav.sosialhjelp.avtaler.enhetsregisteret.EnhetsregisteretService
import no.nav.sosialhjelp.avtaler.kommune.AvtaleResponse
import java.net.URI

private val log = KotlinLogging.logger { }
private val sikkerLog = KotlinLogging.logger("tjenestekall")

class AvtaleService(
    private val altinnService: AltinnService,
    private val digipostService: DigipostService,
    private val enhetsregisteretService: EnhetsregisteretService,
    val databaseContext: DatabaseContext,
) {

    suspend fun hentAvtaler(fnr: String, tjeneste: Avgiver.Tjeneste, token: String?): List<AvtaleResponse> {
        val avgivereFiltrert = altinnService.hentAvgivere(fnr = fnr, tjeneste = tjeneste, token = token)
            .filter { avgiver ->
                val orgnr = avgiver.orgnr
                val enhet = enhetsregisteretService.hentOrganisasjonsenhet(orgnr)
                if (enhet == null) {
                    false
                } else {
                    log.info("Hentet enhet med orgnr: $orgnr")
                    enhet.organisasjonsform.erKommune()
                }
            }

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

    suspend fun signerAvtale(fnr: String, avtaleRequest: AvtaleRequest, navnInnsender: String): URI {
        log.info("Sender avtale til e-signering for ${avtaleRequest.orgnr}")

        val avtale = Avtale(
            orgnr = avtaleRequest.orgnr,
            avtaleversjon = "1.0",
            navn_innsender = navnInnsender
        )
        val digipostResponse = digipostService.sendTilSignering(fnr, avtale)

        lagreDigipostResponse(avtaleRequest.orgnr, digipostResponse)

        return digipostResponse.redirectUrl
    }

    private suspend fun lagreDigipostResponse(orgnr: String, digipostResponse: DigipostResponse) {
        val digipostJobbData = DigipostJobbData(
            orgnr = orgnr,
            directJobReference = digipostResponse.reference,
            signerUrl = digipostResponse.signerUrl,
            statusQueryToken = null
        )
        transaction(databaseContext) { ctx ->
            ctx.digipostJobbDataStore.lagreDigipostResponse(digipostJobbData)
        }
    }

    suspend fun sjekkAvtaleStatus(navnInnsender: String, orgnr: String, statusQueryToken: String): Avtale {
        val avtale = Avtale(
            orgnr = orgnr,
            avtaleversjon = "1.0",
            navn_innsender = navnInnsender
        )
        val digipostJobbData = hentDigipostJobb(orgnr)
        if (digipostJobbData == null) {
            log.info("Kunne ikke hente signeringsstatus for orgnr $orgnr")
            return avtale
        }

        val avtaleErSignert = digipostService.erSigneringsstatusCompleted(
            statusQueryToken,
            digipostJobbData.directJobReference,
            digipostJobbData.signerUrl
        )

        if (!avtaleErSignert) {
            log.info("Avtale for orgnr $orgnr er ikke signert")
            return avtale
        }
        return lagreAvtalestatus(avtale)
    }

    private suspend fun hentDigipostJobb(orgnr: String): DigipostJobbData? =
        transaction(databaseContext) { ctx ->
            ctx.digipostJobbDataStore.hentDigipostJobb(orgnr)
        }

    private suspend fun lagreAvtalestatus(avtale: Avtale): Avtale {
        log.info("Oppretter avtale for ${avtale.orgnr}")

        transaction(databaseContext) { ctx ->
            ctx.avtaleStore.lagreAvtale(avtale)
        }

        log.info("Lagret avtale for ${avtale.orgnr}")
        return avtale
    }
}
