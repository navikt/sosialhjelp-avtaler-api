package no.nav.sosialhjelp.avtaler.digipost

import no.nav.sosialhjelp.avtaler.avtaler.Avtale

class DigipostService(private val digipostClient: DigipostClient) {
    fun sendTilSignering(fnr: String, avtale: Avtale): DigipostResponse {
        return digipostClient.sendTilSignering(fnr, avtale)
    }
}
