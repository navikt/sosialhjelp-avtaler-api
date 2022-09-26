package no.nav.sosialhjelp.avtaler.avtaler

class AvtaleService {
    fun hentAvtale(): Avtale {
        return Avtale(tittel = "Du kan signere avtale")
    }
}
