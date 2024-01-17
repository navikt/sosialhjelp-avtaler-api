package no.nav.sosialhjelp.avtaler.kommune

import java.time.LocalDateTime

data class AvtaleResponse(
    val orgnr: String,
    val navn: String,
    val avtaleversjon: String? = null,
    val opprettet: LocalDateTime? = null,
    val dokumentStatus: DokumentStatus? = null,
)

enum class DokumentStatus {
    SUKSESS,
    ERROR,
}
