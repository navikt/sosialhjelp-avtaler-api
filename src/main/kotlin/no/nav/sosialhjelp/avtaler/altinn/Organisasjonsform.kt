package no.nav.sosialhjelp.avtaler.altinn

data class Organisasjonsform(val kode: String) {
    fun erKommune(): Boolean {
        return kode == "KOMM"
    }
}
