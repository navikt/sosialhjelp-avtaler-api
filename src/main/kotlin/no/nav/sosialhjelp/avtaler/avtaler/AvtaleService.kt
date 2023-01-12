package no.nav.sosialhjelp.avtaler.avtaler

import mu.KotlinLogging
import no.nav.sosialhjelp.avtaler.altinn.AltinnService
import no.nav.sosialhjelp.avtaler.altinn.Avgiver
import no.nav.sosialhjelp.avtaler.db.DatabaseContext
import no.nav.sosialhjelp.avtaler.db.transaction
import no.nav.sosialhjelp.avtaler.digipost.DigipostJobbData
import no.nav.sosialhjelp.avtaler.digipost.DigipostResponse
import no.nav.sosialhjelp.avtaler.digipost.DigipostService
import no.nav.sosialhjelp.avtaler.kommune.AvtaleResponse
import java.io.InputStream
import java.net.URI

private val log = KotlinLogging.logger { }
private val sikkerLog = KotlinLogging.logger("tjenestekall")

class AvtaleService(
    private val altinnService: AltinnService,
    private val digipostService: DigipostService,
    val databaseContext: DatabaseContext,
) {

    suspend fun hentAvtaler(fnr: String, tjeneste: Avgiver.Tjeneste, token: String?): List<AvtaleResponse> {
        val avgivereFiltrert = altinnService.hentAvgivere(fnr = fnr, tjeneste = tjeneste, token = token)
            .filter { avgiver ->
                avgiver.erKommune().also { log.info("Hentet enhet med orgnr: ${avgiver.orgnr}") }
            }
        sikkerLog.info("Filtrert avgivere for fnr: $fnr, tjeneste: $tjeneste, avgivere: $avgivereFiltrert")

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
        log.info("Sender avtale til e-signering for orgnummer ${avtaleRequest.orgnr}")

        val avtale = Avtale(
            orgnr = avtaleRequest.orgnr,
            avtaleversjon = "1.0",
            navn_innsender = navnInnsender,
            erSignert = false
        )
        val digipostResponse = digipostService.sendTilSignering(fnr, avtale)

        lagreDigipostResponse(avtaleRequest.orgnr, digipostResponse)

        return digipostResponse.redirectUrl
    }

    private suspend fun lagreDigipostResponse(orgnr: String, digipostResponse: DigipostResponse) {
        val digipostJobbData = DigipostJobbData(
            orgnr = orgnr,
            directJobReference = digipostResponse.reference,
            statusUrl = digipostResponse.signerUrl,
            statusQueryToken = null,
            signertDokument = null
        )
        transaction(databaseContext) { ctx ->
            ctx.digipostJobbDataStore.lagreDigipostResponse(digipostJobbData)
        }
        log.info("Lagret DigipostJobbData for orgnr $orgnr")
    }

    suspend fun sjekkAvtaleStatusOgLagreSignertDokument(navnInnsender: String, orgnr: String, statusQueryToken: String): Avtale {
        val avtale = Avtale(
            orgnr = orgnr,
            avtaleversjon = "1.0",
            navn_innsender = navnInnsender,
            erSignert = false
        )
        var digipostJobbData = hentDigipostJobb(orgnr) ?: return avtale.apply { log.error("Kunne ikke hente signeringsstatus for orgnr $orgnr") }
        digipostJobbData.copy(statusQueryToken = statusQueryToken).apply { digipostJobbData = this }
        val harSignertAvtale = sjekkAvtaleStatus(avtale, digipostJobbData)
        if (harSignertAvtale) { // vi lagrer bare avtaler som er signert - hvordan skille mellom a
            hentSignertAvtaleDokument(digipostJobbData)
        }

        return avtale
    }
    suspend fun sjekkAvtaleStatus(avtale: Avtale, digipostJobbData: DigipostJobbData): Boolean {
        val avtaleErSignert = digipostService.erSigneringsstatusCompleted(
            digipostJobbData.directJobReference, digipostJobbData.statusUrl,
            digipostJobbData.statusQueryToken!!
        )

        if (!avtaleErSignert) {
            oppdaterDigipostJobbData(digipostJobbData)
            return false.apply { log.info("Avtale for orgnr ${avtale.orgnr} er ikke signert") }
        }
        return true.also { lagreAvtalestatus(avtale.apply { erSignert = true }) }
    }

    fun hentSignertAvtaleDokument(digipostJobbData: DigipostJobbData): InputStream? {
        log.info("Henter signert avtale for orgnr ${digipostJobbData.orgnr}")

        if (digipostJobbData.statusQueryToken == null) {
            return null.apply { log.error("Kunne ikke hente signert avtale for orgnr ${digipostJobbData.orgnr}") }
        }

        return digipostService.hentSignertDokument(
            digipostJobbData.statusQueryToken,
            digipostJobbData.directJobReference,
            digipostJobbData.statusUrl
        )
    }

    suspend fun hentSignertAvtaleDokument(orgnr: String): InputStream? {
        log.info("Henter signert avtale for orgnr $orgnr")
        val digipostJobbData = hentDigipostJobb(orgnr)

        if (digipostJobbData?.statusQueryToken == null) {
            return null.apply { log.error("Kunne ikke hente signert avtale for orgnr $orgnr") }
        }

        return digipostJobbData.signertDokument
            ?: digipostService.hentSignertDokument(
                digipostJobbData.statusQueryToken,
                digipostJobbData.directJobReference,
                digipostJobbData.statusUrl
            )
    }

    private suspend fun oppdaterDigipostJobbData(digipostJobbData: DigipostJobbData) {
        transaction(databaseContext) { ctx ->
            ctx.digipostJobbDataStore.oppdaterDigipostJobbData(digipostJobbData)
        }
    }

    private suspend fun hentDigipostJobb(orgnr: String): DigipostJobbData? =
        transaction(databaseContext) { ctx ->
            ctx.digipostJobbDataStore.hentDigipostJobb(orgnr)
        }

    private suspend fun lagreAvtalestatus(avtale: Avtale): Avtale {
        transaction(databaseContext) { ctx ->
            ctx.avtaleStore.lagreAvtale(avtale)
        }

        log.info("Lagret signert avtale for ${avtale.orgnr}")
        return avtale
    }
}
