package no.nav.sosialhjelp.avtaler.altinn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging

private val sikkerLog = KotlinLogging.logger("tjenestekall")

class AltinnService(
    private val altinnClient: AltinnClient,
) {
    // Returnerer en liste med orgnr/navn bruker har tilgang til
    suspend fun hentTilganger(
        fnr: String,
        token: String?,
    ): List<KommuneTilgang> =
        withContext(Dispatchers.IO) {
            val response = altinnClient.hentTilganger(fnr = fnr, token = token)
            if (response == null) {
                sikkerLog.warn { "Ingen tilganger funnet for fnr: $fnr" }
                return@withContext emptyList()
            }
            val flattenedHierarki = response.hierarki.flatMap { it.underenheter + it }
            val tilganger =
                response.tilgangTilOrgNr["nav_sosialtjenester_digisos-avtale"]?.filter { orgnr ->
                    flattenedHierarki.find { it.orgnr == orgnr }?.organisasjonsform == "KOMM"
                } ?: emptySet()
            val orgnrTilNavn = flattenedHierarki.associate { it.orgnr to it.navn }
            sikkerLog.info {
                "tilganger for fnr: $fnr, tilganger: $tilganger"
            }
            tilganger.map { KommuneTilgang(it, orgnrTilNavn[it]!!) }
        }
}

data class KommuneTilgang(
    val orgnr: String,
    val name: String,
)
