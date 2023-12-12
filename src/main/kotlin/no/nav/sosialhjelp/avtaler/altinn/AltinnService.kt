package no.nav.sosialhjelp.avtaler.altinn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging

private val sikkerLog = KotlinLogging.logger("tjenestekall")

class AltinnService(private val altinnClient: AltinnClient) {
    suspend fun hentAvgivere(
        fnr: String,
        tjeneste: Avgiver.Tjeneste,
        token: String?,
    ): List<Avgiver> =
        withContext(Dispatchers.IO) {
            val avgivere = altinnClient.hentAvgivere(fnr = fnr, tjeneste = tjeneste, token = token)
            sikkerLog.info {
                "Avgivere for fnr: $fnr, tjeneste: $tjeneste, avgivere: $avgivere"
            }
            avgivere
        }

    suspend fun harTilgangTilSignering(
        fnr: String,
        orgnr: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            altinnClient
                .hentRettigheter(fnr = fnr, orgnr = orgnr)
                .contains(Avgiver.Tjeneste.AVTALESIGNERING)
        }
}
