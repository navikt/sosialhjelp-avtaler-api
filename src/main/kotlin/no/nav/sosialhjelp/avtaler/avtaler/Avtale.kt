package no.nav.sosialhjelp.avtaler.avtaler

data class Avtale(
    val orgnr: String,
    val navn: String,
    val avtaleversjon: String,
    val opprettet: Boolean
)
