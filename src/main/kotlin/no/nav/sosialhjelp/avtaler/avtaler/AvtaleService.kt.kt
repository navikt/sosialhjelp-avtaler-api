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

    suspend fun hentAvtaler(fnr: String, tjeneste: Avgiver.Tjeneste, token: String?): List<Avtale> {
        val avgivereFiltrert = altinnService.hentAvgivere(fnr = fnr, tjeneste = tjeneste, token = token)

        sikkerLog.info {
            "Filtrert avgivere for fnr: $fnr, tjeneste: $tjeneste, avgivere: $avgivereFiltrert"
        }

        return avgivereFiltrert
            .map {
                Avtale(
                    orgnr = it.orgnr,
                    navn = it.navn,
                    avtaleversjon = "1.0",
                    opprettet = null,
                )
            }
    }

    suspend fun hentAvtale(
        fnr: String,
        orgnr: String,
        tjeneste: Avgiver.Tjeneste,
        token: String?
    ): Avtale? = hentAvtaler(fnr = fnr, tjeneste = tjeneste, token = token).associateBy {
        it.orgnr
    }[orgnr]

    suspend fun opprettAvtale(avtale: AvtaleRequest, fnrInnsender: String, token: String?): Avtale {
        if (!altinnService.harTilgangTilSignering(fnrInnsender, avtale.orgnr)) {
            throw AvtaleManglerTilgangException(avtale.orgnr)
        }

        log.info("Oppretter avtale for ${avtale.orgnr}")
        val nyAvtale = Avtale(
            orgnr = avtale.orgnr,
            navn = "",
            avtaleversjon = "1.0",
            opprettet = LocalDateTime.now()
        )
        nyAvtale.opprettet = LocalDateTime.now()
        return nyAvtale
    }
}
