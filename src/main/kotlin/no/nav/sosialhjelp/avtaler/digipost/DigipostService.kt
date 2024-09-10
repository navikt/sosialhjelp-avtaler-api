package no.nav.sosialhjelp.avtaler.digipost

import no.digipost.signature.client.direct.DirectJobStatusResponse
import no.nav.sosialhjelp.avtaler.avtaler.Avtale
import no.nav.sosialhjelp.avtaler.db.DatabaseContext
import no.nav.sosialhjelp.avtaler.db.transaction
import java.io.InputStream
import java.net.URI
import java.util.UUID

class DigipostService(
    private val digipostClient: DigipostClient,
    private val databaseContext: DatabaseContext,
) {
    fun sendTilSignering(
        fnr: String,
        avtale: Avtale,
        dokument: ByteArray,
        navn: String,
    ): DigipostResponse = digipostClient.sendTilSignering(fnr, avtale, dokument, navn)

    fun getJobStatus(
        jobbReference: String,
        statusUrl: URI,
        statusQueryToken: String,
    ): DirectJobStatusResponse = digipostClient.getDirectJobStatus(jobbReference, statusUrl, statusQueryToken)

    fun hentSignertDokument(
        statusQueryToken: String,
        directJobReference: String,
        statusUrl: URI,
    ): InputStream? = digipostClient.hentSignertAvtale(statusQueryToken, directJobReference, statusUrl)

    suspend fun oppdaterDigipostJobbData(
        digipostJobbData: DigipostJobbData,
        statusQueryToken: String? = digipostJobbData.statusQueryToken,
        signertDokument: InputStream? = null,
    ) {
        transaction(databaseContext) { ctx ->
            ctx.digipostJobbDataStore.oppdaterDigipostJobbData(
                digipostJobbData.copy(statusQueryToken = statusQueryToken, signertDokument = signertDokument),
            )
        }
    }

    suspend fun hentDigipostJobb(uuid: UUID): DigipostJobbData? =
        transaction(databaseContext) { ctx ->
            ctx.digipostJobbDataStore.hentDigipostJobb(uuid)
        }
}
