package no.nav.sosialhjelp.avtaler.secretmanager

data class DigisosKeyStoreCredentials(
    val alias: String,
    val password: String,
    val type: String,
)
