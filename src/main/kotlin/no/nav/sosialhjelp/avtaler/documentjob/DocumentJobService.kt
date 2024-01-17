package no.nav.sosialhjelp.avtaler.documentjob

import com.google.common.net.MediaType
import mu.KotlinLogging
import no.nav.sosialhjelp.avtaler.avtaler.Avtale
import no.nav.sosialhjelp.avtaler.avtaler.AvtaleService
import no.nav.sosialhjelp.avtaler.digipost.DigipostJobbData
import no.nav.sosialhjelp.avtaler.digipost.DigipostService
import no.nav.sosialhjelp.avtaler.ereg.EregClient
import no.nav.sosialhjelp.avtaler.gcpbucket.GcpBucket

private val log = KotlinLogging.logger { }

class DocumentJobService(
    private val digipostService: DigipostService,
    private val gcpBucket: GcpBucket,
    private val eregClient: EregClient,
) {
    suspend fun lastNedOgLagreAvtale(
        digipostJobbData: DigipostJobbData,
        avtale: Avtale,
    ) = runCatching {
        digipostService.hentSignertDokument(
            digipostJobbData.statusQueryToken ?: error("Kunne ikke hente signert dokument: StatusQueryToken = null"),
            digipostJobbData.directJobReference,
            digipostJobbData.statusUrl,
        ) ?: error("PAdES ikke tilgjengelig på digipost jobb")
    }.onFailure {
        log.error("Fikk ikke hentet dokument fra digipost", it)
    }.mapCatching {
        digipostService.oppdaterDigipostJobbData(
            digipostJobbData,
            statusQueryToken = digipostJobbData.statusQueryToken,
            signertDokument = it,
        )

        val kommunenavn = eregClient.hentEnhetNavn(digipostJobbData.orgnr)
        val blobNavn = AvtaleService.lagFilnavn(kommunenavn, avtale.opprettet)
        val metadata = mapOf("navnInnsender" to avtale.navn_innsender, "signertTidspunkt" to avtale.opprettet.toString())
        gcpBucket.lagreBlob(blobNavn, MediaType.PDF, metadata, it.readAllBytes())
        log.info("Lagret signert avtale i bucket for orgnr ${avtale.orgnr}")
    }
}
