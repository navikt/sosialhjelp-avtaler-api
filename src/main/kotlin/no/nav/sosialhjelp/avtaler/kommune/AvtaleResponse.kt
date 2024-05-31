package no.nav.sosialhjelp.avtaler.kommune

import java.time.LocalDateTime
import java.util.UUID

data class AvtaleResponse(
    val uuid: UUID,
    val navn: String,
    val navnInnsender: String,
    val avtaleversjon: String? = null,
    val opprettet: LocalDateTime? = null,
    val erSignert: Boolean = false,
)

data class KommuneResponse(
    val orgnr: String,
    val navn: String,
    val avtaler: List<AvtaleResponse>,
)
