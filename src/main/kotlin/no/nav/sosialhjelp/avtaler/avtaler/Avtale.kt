package no.nav.sosialhjelp.avtaler.avtaler

import java.time.LocalDateTime

data class Avtale(
    val orgnr: String,
    val navn: String,
    val avtaleversjon: String?,
    val opprettet: LocalDateTime?
)
