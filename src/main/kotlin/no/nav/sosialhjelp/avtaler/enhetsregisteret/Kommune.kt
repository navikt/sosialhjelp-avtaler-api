package no.nav.sosialhjelp.avtaler.enhetsregisteret

data class Kommune(
    val organisasjonsnummer: String,
    val navn: String,
    val organisasjonsform: Organisasjonsform,
)

data class Organisasjonsform(val kode: String) {
    fun erKommune(): Boolean {
        return kode == "KOMM"
    }
}
