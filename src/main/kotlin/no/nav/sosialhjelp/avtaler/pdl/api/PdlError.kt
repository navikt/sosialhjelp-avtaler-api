package no.nav.sosialhjelp.avtaler.pdl.api

data class PdlError(
    val message: String,
    val locations: List<PdlErrorLocation>,
    val path: List<String>?,
    val extensions: PdlErrorExtension
) {
    fun errorMessage(): String {
        return "$message with code: ${extensions.code} and classification: ${extensions.classification}"
    }
}

data class PdlErrorLocation(
    val line: Int?,
    val column: Int?
)

data class PdlErrorExtension(
    val code: String?,
    val classification: String
)
