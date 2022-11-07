package no.nav.sosialhjelp.avtaler.pdl.api

data class HentPersonRequest(
    val query: String,
    val variables: Variables
)

data class Variables(
    val ident: String
)
