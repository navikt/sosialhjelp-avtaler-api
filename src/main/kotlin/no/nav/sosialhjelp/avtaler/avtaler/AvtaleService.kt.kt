package no.nav.sosialhjelp.avtaler.avtaler

import mu.KotlinLogging
import no.nav.sosialhjelp.avtaler.altinn.AltinnService
import no.nav.sosialhjelp.avtaler.altinn.Avgiver
import java.time.LocalDateTime

private val log = KotlinLogging.logger { }
private val sikkerLog = KotlinLogging.logger("tjenestekall")

class AvtaleService(
    private val altinnService: AltinnService,
) {
    private val avtaler = mutableListOf(
        Avtale(orgnr = "0000", navn = "Null kommune", avtaleversjon = "1.0", opprettet = null),
        Avtale(orgnr = "0001", navn = "En kommune", avtaleversjon = "1.0", opprettet = null)
    )
    suspend fun hentAvtale(orgnr: String, fnr: String, tjeneste: Avgiver.Tjeneste, token: String?): Avtale {
        val avgivereFiltrert = altinnService.hentAvgivere(fnr = fnr, tjeneste = tjeneste, token = token)

        sikkerLog.info {
            "Filtrert avgivere for fnr: $fnr, tjeneste: $tjeneste, avgivere: $avgivereFiltrert"
        }
        log.info("Henter avtale for kommune $orgnr")
        return avtaler.filter { it.orgnr == orgnr }.first()
    }

    suspend fun opprettAvtale(avtale: AvtaleRequest, fnrInnsender: String, token: String?): Avtale {
        if (!altinnService.harTilgangTilSignering(fnrInnsender, avtale.orgnr)) {
            throw AvtaleManglerTilgangException(avtale.orgnr)
        }

        log.info("Oppretter avtale for ${avtale.orgnr}")
        val avtale = hentAvtale(avtale.orgnr, fnrInnsender, Avgiver.Tjeneste.AVTALESIGNERING, token = token)
        avtale.opprettet = LocalDateTime.now()
        return avtale
    }
}
