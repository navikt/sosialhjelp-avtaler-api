package no.nav.sosialhjelp.avtaler.digipost

import no.nav.sosialhjelp.avtaler.avtaler.Avtale
import java.net.URI

class DigipostService(private val digipostClient: DigipostClient) {
    fun sendTilSignering(fnr: String, avtale: Avtale): URI {
        return digipostClient.sendTilSignering(fnr, avtale)
    }
}
