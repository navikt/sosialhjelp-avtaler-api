package no.nav.sosialhjelp.avtaler.enhetsregisteret

import mu.KotlinLogging

private val log = KotlinLogging.logger { }

class EnhetsregisteretService(
    private val enhetsregisteretClient: EnhetsregisteretClient,
) {
    suspend fun hentOrganisasjonsenhet(orgnr: String): Kommune? {
        log.info { "Henter organisasjonsenhet med orgnr: $orgnr" }

        val enhet = enhetsregisteretClient.hentOrganisasjonsenhet(orgnr)
        if (enhet != null) {
            log.info { "Hentet enhet med orgnr: $orgnr fra tjeneste" }
            return enhet
        }

        log.info { "Klarte ikke å finne en organisasjonsenhet for orgnr: $orgnr" }
        return null
    }
}
