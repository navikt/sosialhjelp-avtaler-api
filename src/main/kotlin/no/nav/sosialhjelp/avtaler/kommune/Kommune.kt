package no.nav.sosialhjelp.avtaler.kommune

import java.time.LocalDateTime

data class Kommune(
    val orgnr: String,
    val navn: String,
    val opprettet: LocalDateTime?
)
