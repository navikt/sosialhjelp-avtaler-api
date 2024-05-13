package no.nav.sosialhjelp.avtaler.digipost

import java.io.InputStream
import java.net.URI
import java.util.UUID

data class DigipostJobbData(
    val uuid: UUID,
    val directJobReference: String,
    val statusUrl: URI,
    val statusQueryToken: String?,
    val signertDokument: InputStream?,
)
