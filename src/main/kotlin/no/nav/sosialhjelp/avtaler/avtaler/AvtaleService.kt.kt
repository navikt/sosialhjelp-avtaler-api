package no.nav.sosialhjelp.avtaler.avtaler

import mu.KotlinLogging
import no.nav.sosialhjelp.avtaler.kommune.Kommune

private val log = KotlinLogging.logger { }

class AvtaleService {
    fun hentAvtale(kommunenummer: String): List<Avtale> {
        log.info("Henter avtale for kommune $kommunenummer")
        return listOf(Avtale(orgnr = kommunenummer, navn = "Kommunenavn", avtaleversjon = "1.0", opprettet = null))
    }

    fun opprettAvtale(kommune: Kommune): Avtale {
        log.info("Oppretter avtale for $kommune")
        return hentAvtale(kommune.orgnr)[0]
    }
}
