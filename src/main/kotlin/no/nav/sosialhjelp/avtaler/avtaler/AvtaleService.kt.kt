package no.nav.sosialhjelp.avtaler.avtaler

import mu.KotlinLogging

private val log = KotlinLogging.logger { }

class AvtaleService {
    fun hentAvtale(): Avtale {
        log.info("Henter avtale")
        return Avtale(tittel = "Du kan signere avtale", erSignert = false)
    }
}
