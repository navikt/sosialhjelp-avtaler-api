package no.nav.sosialhjelp.avtaler.altinn

class AltinnService {
    fun hentKommunerFor(fnr: String): List<String> {
        return listOf("0000", "0001")
    }
}
