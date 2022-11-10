package no.nav.sosialhjelp.avtaler.digipost

import no.nav.sosialhjelp.avtaler.avtaler.Avtale
import java.net.URI

class DigipostService(private val digipostClient: DigipostClient) {
    fun sendTilSignering(fnr: String, avtale: Avtale): URI? {
        return digipostClient.sendTilSignering(fnr, avtale)
    }

    fun sjekkSigneringstatus(fnr: String, avtale: Avtale, statusQueryToken: String): Boolean {
        return digipostClient.sjekkSigneringstatus(fnr, avtale, statusQueryToken)
    }
}
