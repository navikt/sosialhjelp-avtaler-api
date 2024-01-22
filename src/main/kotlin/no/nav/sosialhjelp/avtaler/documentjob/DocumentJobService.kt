package no.nav.sosialhjelp.avtaler.documentjob

import com.google.common.net.MediaType
import mu.KotlinLogging
import no.nav.sosialhjelp.avtaler.avtaler.Avtale
import no.nav.sosialhjelp.avtaler.avtaler.AvtaleService
import no.nav.sosialhjelp.avtaler.digipost.DigipostJobbData
import no.nav.sosialhjelp.avtaler.digipost.DigipostService
import no.nav.sosialhjelp.avtaler.ereg.EregClient
import no.nav.sosialhjelp.avtaler.gcpbucket.GcpBucket
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
        val dbInputStream = ByteArrayInputStream(outputStream.toByteArray())
        val bucketInputStream = ByteArrayInputStream(outputStream.toByteArray())

        dbInputStream.use {
            digipostService.oppdaterDigipostJobbData(
                digipostJobbData,
                statusQueryToken = digipostJobbData.statusQueryToken,
                signertDokument = dbInputStream,
            )
        }

        val kommunenavn = eregClient.hentEnhetNavn(digipostJobbData.orgnr)
        val blobNavn = AvtaleService.lagFilnavn(kommunenavn, avtale.opprettet)
        val metadata = mapOf("navnInnsender" to avtale.navn_innsender, "signertTidspunkt" to avtale.opprettet.toString())
        bucketInputStream.use {
            gcpBucket.lagreBlob(blobNavn, MediaType.PDF, metadata, bucketInputStream.readAllBytes())
        }
        log.info("Lagret signert avtale i bucket for orgnr ${avtale.orgnr}")
    }
}
