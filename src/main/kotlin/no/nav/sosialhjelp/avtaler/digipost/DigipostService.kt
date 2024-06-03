package no.nav.sosialhjelp.avtaler.digipost

import no.digipost.signature.client.direct.DirectJobStatus
import no.nav.sosialhjelp.avtaler.db.DatabaseContext
import no.nav.sosialhjelp.avtaler.db.transaction
import java.io.InputStream
import java.net.URI
import java.time.LocalDateTime
import java.util.UUID

data class DigipostAvtale(
    val orgnr: String,
    val avtaleversjon: String? = null,
    val navn_innsender: String,
    val erSignert: Boolean,
    val opprettet: LocalDateTime = LocalDateTime.now(),
)

class DigipostService(private val digipostClient: DigipostClient, private val databaseContext: DatabaseContext) {
    fun sendTilSignering(
        fnr: String,
        avtale: DigipostAvtale,
    ): DigipostResponse {
        return digipostClient.sendTilSignering(fnr, avtale)
    }

    fun erSigneringsstatusCompleted(
        jobbReference: String,
        statusUrl: URI,
        statusQueryToken: String,
    ): Boolean {
        return digipostClient.sjekkSigneringsstatus(jobbReference, statusUrl, statusQueryToken) == DirectJobStatus.COMPLETED_SUCCESSFULLY
    }

    fun hentSignertDokument(
        statusQueryToken: String,
        directJobReference: String,
        statusUrl: URI,
    ): InputStream? {
        return digipostClient.hentSignertAvtale(statusQueryToken, directJobReference, statusUrl)
    }

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
