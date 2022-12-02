package no.nav.sosialhjelp.avtaler.digipost

import java.net.URI

data class DigipostJobbData(
    val orgnr: String,
    val directJobReference: Long,
    val signerUrl: URI
)
