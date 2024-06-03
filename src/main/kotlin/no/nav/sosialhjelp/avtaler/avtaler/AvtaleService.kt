package no.nav.sosialhjelp.avtaler.avtaler

import mu.KotlinLogging
import no.nav.sosialhjelp.avtaler.Configuration
import no.nav.sosialhjelp.avtaler.altinn.AltinnService
import no.nav.sosialhjelp.avtaler.altinn.Avgiver
import no.nav.sosialhjelp.avtaler.db.DatabaseContext
import no.nav.sosialhjelp.avtaler.db.transaction
import no.nav.sosialhjelp.avtaler.digipost.DigipostAvtale
import no.nav.sosialhjelp.avtaler.digipost.DigipostJobbData
import no.nav.sosialhjelp.avtaler.digipost.DigipostResponse
import no.nav.sosialhjelp.avtaler.digipost.DigipostService
import no.nav.sosialhjelp.avtaler.documentjob.DocumentJobService
import no.nav.sosialhjelp.avtaler.kommune.AvtaleResponse
import no.nav.sosialhjelp.avtaler.kommune.KommuneResponse
import no.nav.sosialhjelp.avtaler.pdl.PersonNavnService
import no.nav.sosialhjelp.avtaler.slack.Slack
import java.io.InputStream
import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

private val log = KotlinLogging.logger { }
private val sikkerLog = KotlinLogging.logger("tjenestekall")

class AvtaleService(
    private val altinnService: AltinnService,
    private val digipostService: DigipostService,
    private val documentJobService: DocumentJobService,
    private val databaseContext: DatabaseContext,
    private val personNavnService: PersonNavnService,
) {
    suspend fun hentKommuner(
        fnr: String,
        tjeneste: Avgiver.Tjeneste,
        token: String?,
    ): List<KommuneResponse> {
        val avgivereFiltrert = hentAvgivere(fnr, tjeneste, token)

        val avtaler =
            transaction(databaseContext) { ctx ->
                ctx.avtaleStore.hentAvtalerForOrganisasjoner(avgivereFiltrert.map { it.orgnr })
            }

        val avtalerByOrgnr = avtaler.groupBy { it.orgnr }
        return avgivereFiltrert
            .map { avgiver ->
                KommuneResponse(
                    orgnr = avgiver.orgnr,
                    navn = avgiver.navn,
                    avtaler =
                        avtalerByOrgnr[avgiver.orgnr]?.map {
                            AvtaleResponse(it.uuid, it.navn, it.navn_innsender, it.avtaleversjon, it.opprettet, it.erSignert)
                        } ?: emptyList(),
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
        uuid: UUID,
        tjeneste: Avgiver.Tjeneste,
        token: String?,
    ): AvtaleResponse? {
        val avgivereFiltrert = hentAvgivere(fnr, tjeneste, token)

        val avtale =
            transaction(databaseContext) { ctx ->
                ctx.avtaleStore.hentAvtale(uuid)
            }

        check(avtale?.orgnr in avgivereFiltrert.map { it.orgnr }) {
            "Forespurt avtale $uuid er ikke fra en kommune som er tilknyttet brukerens fnr"
        }

        return avtale?.let {
            AvtaleResponse(
                uuid = it.uuid,
                avtaleversjon = it.avtaleversjon,
                opprettet = it.opprettet,
                navn = it.navn,
                navnInnsender = it.navn_innsender,
            )
        }
    }

    private suspend fun hentAvgivere(
        fnr: String,
        tjeneste: Avgiver.Tjeneste,
        token: String?,
    ): List<Avgiver> =
        altinnService.hentAvgivere(fnr = fnr, tjeneste = tjeneste, token = token)
            .filter { avgiver ->
                avgiver.erKommune().also { log.info("Hentet enhet med orgnr: ${avgiver.orgnr}") }
            }.also { sikkerLog.info("Filtrert avgivere for fnr: $fnr, tjeneste: $tjeneste, avgivere: $this") }

    suspend fun signerAvtale(
        fnr: String,
        avtaleRequest: AvtaleRequest,
        navnInnsender: String,
    ): URI {
        log.info("Sender avtale til e-signering for orgnummer ${avtaleRequest.orgnr}")

        val avtale =
            DigipostAvtale(
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
                uuid = UUID.randomUUID(),
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
        uuid: UUID,
        fnr: String,
        token: String,
        statusQueryToken: String,
    ): AvtaleResponse? {
        val digipostJobbData = digipostService.hentDigipostJobb(uuid)
        if (digipostJobbData == null) {
            log.error("Kunne ikke hente signeringsstatus for uuid $uuid")
            return null
        }
        digipostService.oppdaterDigipostJobbData(digipostJobbData, statusQueryToken = statusQueryToken)
        val avtale = hentAvtale(uuid) ?: error("Finnes ikke noen avtale med uuid $uuid")
        val signertAvtale =
            if (!erAvtaleSignert(digipostJobbData, statusQueryToken)) {
                log.info("Avtale for orgnr ${avtale.orgnr} er ikke signert")
                return null
            } else {
                val navn = personNavnService.getFulltNavn(fnr, token)
                lagreAvtale(avtale.copy(erSignert = true, navn_innsender = navn))
            }

        log.info("Avtale for orgnr ${avtale.orgnr} er signert")

        documentJobService.lastNedOgLagreAvtale(digipostJobbData, signertAvtale)
        return AvtaleResponse(
            uuid = signertAvtale.uuid,
            avtaleversjon = signertAvtale.avtaleversjon,
            opprettet = signertAvtale.opprettet,
            navn = signertAvtale.navn,
            navnInnsender = signertAvtale.navn_innsender,
        )
    }

    private fun erAvtaleSignert(
        digipostJobbData: DigipostJobbData,
        statusQueryToken: String,
    ): Boolean =
        digipostService.erSigneringsstatusCompleted(
            digipostJobbData.directJobReference,
            digipostJobbData.statusUrl,
            statusQueryToken,
        )

    private fun hentSignertAvtaleDokumentFraDigipost(
        digipostJobbData: DigipostJobbData,
        statusQueryToken: String?,
    ): InputStream? {
        log.info("Henter signert avtale for uuid ${digipostJobbData.uuid} fra Digipost")

        if (statusQueryToken == null) {
            log.error("StatusQueryToken er null. Kunne ikke hente signert avtale for uuid ${digipostJobbData.uuid}")
            return null
        }

        return digipostService.hentSignertDokument(
            statusQueryToken,
            digipostJobbData.directJobReference,
            digipostJobbData.statusUrl,
        )
    }

    suspend fun hentSignertAvtaleDokumentFraDatabaseEllerDigipost(uuid: UUID): InputStream? {
        val digipostJobbData =
            digipostService.hentDigipostJobb(uuid)

        if (digipostJobbData == null) {
            log.error("Kunne ikke hente digipost jobb-info fra database for uuid $uuid")
            return null
        }

        if (digipostJobbData.signertDokument != null) {
            log.info { "Hentet signert avtale for uuid $uuid fra database" }
            return digipostJobbData.signertDokument
        }

        return hentSignertAvtaleDokumentFraDigipost(
            digipostJobbData,
            digipostJobbData.statusQueryToken,
        )
    }

    suspend fun hentAvtale(uuid: UUID): Avtale? =
        transaction(databaseContext) { ctx ->
            ctx.avtaleStore.hentAvtale(uuid)
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
            }.pdf"
        }
    }
}
