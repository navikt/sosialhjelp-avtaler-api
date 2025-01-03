package no.nav.sosialhjelp.avtaler.documentjob

import com.google.common.net.MediaType
import mu.KotlinLogging
import no.nav.sosialhjelp.avtaler.Configuration
import no.nav.sosialhjelp.avtaler.avtaler.Avtale
import no.nav.sosialhjelp.avtaler.digipost.DigipostJobbData
import no.nav.sosialhjelp.avtaler.digipost.DigipostService
import no.nav.sosialhjelp.avtaler.ereg.EregClient
import no.nav.sosialhjelp.avtaler.gcpbucket.GcpBucket
import no.nav.sosialhjelp.avtaler.slack.Slack
import no.nav.sosialhjelp.avtaler.utils.format
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

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
    }.mapCatching { fileIS ->
        val outputStream = ByteArrayOutputStream()
        fileIS.use {
            it.transferTo(outputStream)
        }
        val byteArray = outputStream.toByteArray()
        val dbInputStream = ByteArrayInputStream(byteArray)
        val bucketInputStream = ByteArrayInputStream(byteArray)

        dbInputStream.use {
            digipostService.oppdaterDigipostJobbData(
                digipostJobbData,
                statusQueryToken = digipostJobbData.statusQueryToken,
                signertDokument = dbInputStream,
            )
        }

        val kommunenavn = eregClient.hentEnhetNavn(avtale.orgnr)

        val signertTidspunkt = avtale.signert_tidspunkt.format()
        val blobNavn = "${avtale.navn} - $kommunenavn - $signertTidspunkt.pdf"
        val metadata =
            mapOf(
                "navnInnsender" to (avtale.navn_innsender ?: error("Har ikke navn på innsender")),
                "signertTidspunkt" to avtale.signert_tidspunkt.toString(),
            )
        bucketInputStream.use {
            gcpBucket.lagreBlob(blobNavn, MediaType.PDF, metadata, bucketInputStream.readAllBytes())
        }
        log.info("Lagret signert avtale i bucket for orgnr ${avtale.orgnr}, uuid ${avtale.uuid}")
        if (Configuration.dev || Configuration.prod) {
            Slack.post("Ny avtale (${avtale.navn}) signert for orgnr=${avtale.orgnr}.")
        }
    }
}
