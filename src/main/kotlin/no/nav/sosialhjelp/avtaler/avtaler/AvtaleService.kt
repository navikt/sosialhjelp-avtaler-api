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

    suspend fun sjekkAvtaleStatusOgLagreSignertDokument(navnInnsender: String, orgnr: String, statusQueryToken: String): AvtaleResponse? {
        val avtale = Avtale(
            orgnr = orgnr,
            avtaleversjon = "1.0",
            navn_innsender = navnInnsender,
            erSignert = false
        )
        val digipostJobbData = hentDigipostJobb(orgnr)
        if (digipostJobbData == null) {
            log.error("Kunne ikke hente signeringsstatus for orgnr $orgnr")
            return null
        }
        val harSignertAvtale = sjekkAvtaleStatus(avtale, digipostJobbData, statusQueryToken)
        if (!harSignertAvtale) {
            oppdaterDigipostJobbData(digipostJobbData, statusQueryToken = statusQueryToken)
            log.info("Avtale for orgnr ${avtale.orgnr} er ikke signert")
            return null
        }
        log.info("Avtale for orgnr ${avtale.orgnr} er signert")
        oppdaterDigipostJobbData(
            digipostJobbData,
            statusQueryToken = statusQueryToken,
            signertDokument = hentSignertAvtaleDokumentFraDigipost(digipostJobbData, statusQueryToken = statusQueryToken,)
        )
        return AvtaleResponse(
            orgnr = avtale.orgnr,
            navn = avtale.navn_innsender,
            avtaleversjon = avtale.avtaleversjon,
            opprettet = avtale.opprettet,
        )
    }
    suspend fun sjekkAvtaleStatus(avtale: Avtale, digipostJobbData: DigipostJobbData, statusQueryToken: String): Boolean {
        val avtaleErSignert = digipostService.erSigneringsstatusCompleted(
            digipostJobbData.directJobReference, digipostJobbData.statusUrl, statusQueryToken
        )

        if (!avtaleErSignert) {
            return false
        }
        lagreAvtale(avtale.copy(erSignert = true))
        return true
    }

    fun hentSignertAvtaleDokumentFraDigipost(digipostJobbData: DigipostJobbData, statusQueryToken: String?): InputStream? {
        log.info("Henter signert avtale for orgnr ${digipostJobbData.orgnr} fra Digipost")

        if (statusQueryToken == null) {
            return null.apply { log.error("StatusQueryToken er null. Kunne ikke hente signert avtale for orgnr ${digipostJobbData.orgnr}") }
        }

        return digipostService.hentSignertDokument(
            statusQueryToken,
            digipostJobbData.directJobReference,
            digipostJobbData.statusUrl
        )
    }

    suspend fun hentSignertAvtaleDokumentFraDatabaseEllerDigipost(orgnr: String): InputStream? {
        val digipostJobbData = hentDigipostJobb(orgnr)
            ?: return null.apply { log.error("Kunne ikke hente digipost jobb-info fra database for orgnr $orgnr") }

        if (digipostJobbData.signertDokument != null) {
            log.info { "Hentet signert avtale for orgnr $orgnr fra database" }
            return digipostJobbData.signertDokument
        }
        return hentSignertAvtaleDokumentFraDigipost(
            digipostJobbData,
            digipostJobbData.statusQueryToken
        )
    }

    private suspend fun oppdaterDigipostJobbData(
        digipostJobbData: DigipostJobbData,
        statusQueryToken: String,
        signertDokument: InputStream? = null
    ) {
        transaction(databaseContext) { ctx ->
            ctx.digipostJobbDataStore.oppdaterDigipostJobbData(digipostJobbData.copy(statusQueryToken = statusQueryToken, signertDokument = signertDokument))
        }
    }

    private suspend fun hentDigipostJobb(orgnr: String): DigipostJobbData? =
        transaction(databaseContext) { ctx ->
            ctx.digipostJobbDataStore.hentDigipostJobb(orgnr)
        }

    private suspend fun lagreAvtale(avtale: Avtale): Avtale {
        transaction(databaseContext) { ctx ->
            ctx.avtaleStore.lagreAvtale(avtale)
        }

        log.info("Lagret signert avtale for ${avtale.orgnr}")
        return avtale
    }
}
