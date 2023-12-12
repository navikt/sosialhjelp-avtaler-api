package no.nav.sosialhjelp.avtaler.pdl

class PersonNavnService(
    private val pdlClient: PdlClient,
) {
    suspend fun getFulltNavn(
        ident: String,
        token: String,
    ): String {
        return pdlClient.hentPerson(ident, token)?.hentPerson?.navn?.firstOrNull()?.fulltNavn()
            ?: throw RuntimeException("Pdl - noe feilet")
    }
}
