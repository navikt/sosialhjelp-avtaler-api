package no.nav.sosialhjelp.avtaler.digipost

import no.digipost.signature.client.direct.DirectJobStatus
import no.nav.sosialhjelp.avtaler.avtaler.Avtale
import java.io.InputStream
import java.net.URI

class DigipostService(private val digipostClient: DigipostClient) {
    fun sendTilSignering(fnr: String, avtale: Avtale): DigipostResponse {
        return digipostClient.sendTilSignering(fnr, avtale)
    }

    fun erSigneringsstatusCompleted(digipostJobbData: DigipostJobbData): Boolean {
        return digipostClient.sjekkSigneringsstatus(digipostJobbData) == DirectJobStatus.COMPLETED_SUCCESSFULLY
    }

    fun hentSignertDokument(statusQueryToken: String, directJobReference: String, statusUrl: URI): InputStream? {
        return digipostClient.hentSignertAvtale(statusQueryToken, directJobReference, statusUrl)
    }
}
