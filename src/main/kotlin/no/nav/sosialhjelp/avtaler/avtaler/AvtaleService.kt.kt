package no.nav.sosialhjelp.avtaler.avtaler

import mu.KotlinLogging

private val log = KotlinLogging.logger { }

class AvtaleService {
    fun hentAvtale(person: Person): Avtale {
        val navn = person.navn
        log.info("Henter avtale for person $navn")
        return Avtale(tittel = "Du kan signere avtale, $navn", erSignert = false)
    }
}
