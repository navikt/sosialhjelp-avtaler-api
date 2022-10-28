package no.nav.sosialhjelp.avtaler.digipost

import no.nav.sosialhjelp.avtaler.avtaler.Avtale

class DigipostService(private val digipostClient: DigipostApiClient) {
    suspend fun sendTilSignering(fnr: String, avtale: Avtale) {
        digipostClient.sendTilSignering(fnr, avtale)
    }
}
