package no.nav.sosialhjelp.avtaler.avtaler

import mu.KotlinLogging
import no.nav.sosialhjelp.avtaler.Configuration
import no.nav.sosialhjelp.avtaler.altinn.AltinnService
import no.nav.sosialhjelp.avtaler.altinn.Avgiver
import no.nav.sosialhjelp.avtaler.db.DatabaseContext
import no.nav.sosialhjelp.avtaler.db.transaction
import no.nav.sosialhjelp.avtaler.digipost.DigipostJobbData
import no.nav.sosialhjelp.avtaler.digipost.DigipostResponse
import no.nav.sosialhjelp.avtaler.digipost.DigipostService
import no.nav.sosialhjelp.avtaler.documentjob.DocumentJobService
import no.nav.sosialhjelp.avtaler.ereg.EregClient
import no.nav.sosialhjelp.avtaler.gcpbucket.GcpBucket
import no.nav.sosialhjelp.avtaler.kommune.AvtaleResponse
import no.nav.sosialhjelp.avtaler.slack.Slack
import java.io.InputStream
import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val log = KotlinLogging.logger { }
private val sikkerLog = KotlinLogging.logger("tjenestekall")

class AvtaleService(
    private val altinnService: AltinnService,
    private val digipostService: DigipostService,
    private val gcpBucket: GcpBucket,
    private val documentJobService: DocumentJobService,
    private val databaseContext: DatabaseContext,
    private val eregClient: EregClient,
) {
    suspend fun hentAvtaler(
        fnr: String,
        tjeneste: Avgiver.Tjeneste,
        token: String?,
    ): List<AvtaleResponse> {
        val avgivereFiltrert =
            altinnService.hentAvgivere(fnr = fnr, tjeneste = tjeneste, token = token)
                .filter { avgiver ->
                    avgiver.erKommune().also { log.info("Hentet enhet med orgnr: ${avgiver.orgnr}") }
                }
        sikkerLog.info("Filtrert avgivere for fnr: $fnr, tjeneste: $tjeneste, avgivere: $avgivereFiltrert")

        val avtaler =
            transaction(databaseContext) { ctx ->
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

    suspend fun hentAvtalerUtenSignertDokument(): List<DigipostJobbData> {
        return transaction(databaseContext) { ctx ->
            ctx.digipostJobbDataStore.hentAlleUtenLagretDokument()
        }
    }

    suspend fun hentAvtale(
        fnr: String,
        orgnr: String,
        tjeneste: Avgiver.Tjeneste,
        token: String?,
    ): AvtaleResponse? =
        hentAvtaler(fnr = fnr, tjeneste = tjeneste, token = token).associateBy {
            it.orgnr
        }[orgnr]

    suspend fun signerAvtale(
        fnr: String,
        avtaleRequest: AvtaleRequest,
        navnInnsender: String,
    ): URI {
        log.info("Sender avtale til e-signering for orgnummer ${avtaleRequest.orgnr}")

        val avtale =
            Avtale(
                orgnr = avtaleRequest.orgnr,
                avtaleversjon = "1.0",
                navn_innsender = navnInnsender,
                erSignert = false,
            )
        val digipostResponse = digipostService.sendTilSignering(fnr, avtale)

        lagreDigipostResponse(avtaleRequest.orgnr, digipostResponse)

        return digipostResponse.redirectUrl
    }

    private suspend fun lagreDigipostResponse(
        orgnr: String,
        digipostResponse: DigipostResponse,
    ) {
        val digipostJobbData =
            DigipostJobbData(
                orgnr = orgnr,
                directJobReference = digipostResponse.reference,
                statusUrl = digipostResponse.signerUrl,
                statusQueryToken = null,
                signertDokument = null,
            )
        transaction(databaseContext) { ctx ->
            ctx.digipostJobbDataStore.lagreDigipostResponse(digipostJobbData)
        }
        log.info("Lagret DigipostJobbData for orgnr $orgnr")
    }

    suspend fun sjekkAvtaleStatusOgLagreSignertDokument(
        navnInnsender: String,
        orgnr: String,
        statusQueryToken: String,
    ): AvtaleResponse? {
        val avtale =
            Avtale(
                orgnr = orgnr,
                avtaleversjon = "1.0",
                navn_innsender = navnInnsender,
                erSignert = false,
            )
        val digipostJobbData = digipostService.hentDigipostJobb(orgnr)
        if (digipostJobbData == null) {
            log.error("Kunne ikke hente signeringsstatus for orgnr $orgnr")
            return null
        }
        if (!erAvtaleSignert(avtale, digipostJobbData, statusQueryToken)) {
            digipostService.oppdaterDigipostJobbData(digipostJobbData, statusQueryToken = statusQueryToken)
            log.info("Avtale for orgnr ${avtale.orgnr} er ikke signert")
            return null
        }
        log.info("Avtale for orgnr ${avtale.orgnr} er signert")

        val dbAvtale = hentAvtale(orgnr)
        if (dbAvtale == null) {
            log.error("Kunne ikke hente avtale fra database for orgnr $orgnr")
            return null
        }
        documentJobService.lastNedOgLagreAvtale(digipostJobbData, dbAvtale)
        return AvtaleResponse(
            orgnr = dbAvtale.orgnr,
            navn = eregClient.hentEnhet(orgnr).navn,
            avtaleversjon = dbAvtale.avtaleversjon,
            opprettet = dbAvtale.opprettet,
        )
    }

    suspend fun erAvtaleSignert(
        avtale: Avtale,
        digipostJobbData: DigipostJobbData,
        statusQueryToken: String,
    ): Boolean {
        val avtaleErSignert =
            digipostService.erSigneringsstatusCompleted(
                digipostJobbData.directJobReference,
                digipostJobbData.statusUrl,
                statusQueryToken,
            )

        if (!avtaleErSignert) {
            return false
        }
        lagreAvtale(avtale.copy(erSignert = true))
        return true
    }

    fun hentSignertAvtaleDokumentFraDigipost(
        digipostJobbData: DigipostJobbData,
        statusQueryToken: String?,
    ): InputStream? {
        log.info("Henter signert avtale for orgnr ${digipostJobbData.orgnr} fra Digipost")

        if (statusQueryToken == null) {
            log.error("StatusQueryToken er null. Kunne ikke hente signert avtale for orgnr ${digipostJobbData.orgnr}")
            return null
        }

        return digipostService.hentSignertDokument(
            statusQueryToken,
            digipostJobbData.directJobReference,
            digipostJobbData.statusUrl,
        )
    }

    suspend fun hentSignertAvtaleDokumentFraDatabaseEllerDigipost(orgnr: String): InputStream? {
        val digipostJobbData =
            digipostService.hentDigipostJobb(orgnr)
                ?: return null.apply { log.error("Kunne ikke hente digipost jobb-info fra database for orgnr $orgnr") }

        if (digipostJobbData.signertDokument != null) {
            log.info { "Hentet signert avtale for orgnr $orgnr fra database" }
            return digipostJobbData.signertDokument
        }

        return hentSignertAvtaleDokumentFraDigipost(
            digipostJobbData,
            digipostJobbData.statusQueryToken,
        )
    }

    suspend fun hentAvtale(orgnr: String): Avtale? =
        transaction(databaseContext) { ctx ->
            ctx.avtaleStore.hentAvtaleForOrganisasjon(orgnr)
        }

    private suspend fun lagreAvtale(avtale: Avtale): Avtale {
        transaction(databaseContext) { ctx ->
            ctx.avtaleStore.lagreAvtale(avtale)
        }

        if (Configuration.dev || Configuration.prod) {
            Slack.post("Ny avtale opprettet for orgnr=${avtale.orgnr}")
        }

        log.info("Lagret signert avtale for ${avtale.orgnr}")
        return avtale
    }

    companion object {
        fun lagFilnavn(
            kommunenavn: String,
            opprettet: LocalDateTime,
        ): String {
            return "Avtale om innsynsflate for NAV Kontaktsenter - Digisos - ${kommunenavn.replace("/", "-")} - ${
                opprettet.format(
                    DateTimeFormatter.ofPattern("dd.MM.yyyy"),
                )
            }"
        }
    }
}
