package no.nav.sosialhjelp.avtaler.digipost

import no.digipost.signature.client.direct.DirectJobStatus
import no.nav.sosialhjelp.avtaler.avtaler.Avtale
import java.net.URI

class DigipostService(private val digipostClient: DigipostClient) {
    fun sendTilSignering(fnr: String, avtale: Avtale): DigipostResponse {
        return digipostClient.sendTilSignering(fnr, avtale)
    }

    fun erSigneringsstatusCompleted(statusQueryToken: String, directJobReference: String, statusUrl: URI): Boolean {
        return digipostClient.sjekkSigneringsstatus(statusQueryToken, directJobReference, statusUrl) == DirectJobStatus.COMPLETED_SUCCESSFULLY
    }

    fun sendTilTestSignering(fnr: String, avtale: Avtale): DigipostResponse {
        return digipostClient.sendTilTestSignering(fnr, avtale)
    }
}
