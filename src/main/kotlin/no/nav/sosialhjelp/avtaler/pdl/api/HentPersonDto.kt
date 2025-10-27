package no.nav.sosialhjelp.avtaler.pdl.api

data class HentPersonResponse(
    val data: PdlHentPerson?,
    val errors: List<PdlError>?,
)

data class PdlHentPerson(
    val hentPerson: HentPersonDto?,
)

data class HentPersonDto(
    val navn: List<PdlPersonNavn>,
)

data class PdlPersonNavn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
) {
    fun fulltNavn(): String = mellomnavn?.let { "$fornavn $it $etternavn" } ?: "$fornavn $etternavn"
}
