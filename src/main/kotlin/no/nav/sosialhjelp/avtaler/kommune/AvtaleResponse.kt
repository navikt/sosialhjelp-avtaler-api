package no.nav.sosialhjelp.avtaler.kommune

import java.time.LocalDateTime
import java.util.UUID

data class AvtaleResponse(
    val uuid: UUID,
    val orgnr: String,
    val navn: String,
    val navnInnsender: String? = null,
    val avtaleversjon: String? = null,
    val opprettet: LocalDateTime? = null,
    val erSignert: Boolean = false,
    val avtaleUrl: String? = "/api/avtale/$uuid/avtale",
    val ingress: String? = null,
    val kvitteringstekst: String? = null,
)

data class KommuneResponse(
    val orgnr: String,
    val navn: String,
    val avtaler: List<AvtaleResponse>,
)
