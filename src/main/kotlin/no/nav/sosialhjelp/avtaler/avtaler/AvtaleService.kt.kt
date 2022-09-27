package no.nav.sosialhjelp.avtaler.avtaler

import mu.KotlinLogging
import java.time.LocalDateTime

private val log = KotlinLogging.logger { }

class AvtaleService {
    private val avtaler = mutableListOf(
        Avtale(orgnr = "0000", navn = "Null kommune", avtaleversjon = "1.0", opprettet = null),
        Avtale(orgnr = "0001", navn = "En kommune", avtaleversjon = "1.0", opprettet = null)
    )
    fun hentAvtale(orgnr: String): Avtale {
        log.info("Henter avtale for kommune $orgnr")
        return avtaler.filter { it.orgnr == orgnr }.first()
    }

    fun opprettAvtale(avtale: AvtaleRequest): Avtale {
        log.info("Oppretter avtale for ${avtale.orgnr}")
        val avtale = hentAvtale(avtale.orgnr)
        avtale.opprettet = LocalDateTime.now()
        return avtale
    }
}
