package no.nav.sosialhjelp.avtaler.avtaler

import mu.KotlinLogging

private val log = KotlinLogging.logger { }

class AvtaleService {
    fun hentAvtale(kommunenummer: String): List<Avtale> {
        log.info("Henter avtale for kommune $kommunenummer")
        return listOf(Avtale(orgnr = kommunenummer, navn = "Kommunenavn", avtaleversjon = "1.0", opprettet = false))
    }

    fun opprettAvtale(kommunenummer: Kommune): Avtale {
        log.info("Oppretter avtale for $kommunenummer")
        return hentAvtale(kommunenummer.orgnr)[0]
    }
}
