package no.nav.sosialhjelp.avtaler.digipost

import java.io.InputStream
import java.net.URI

data class DigipostJobbData(
    val orgnr: String,
    val directJobReference: String,
    val statusUrl: URI,
    val statusQueryToken: String?,
    val signertDokument: InputStream?,
)
