package no.nav.sosialhjelp.avtaler.avtaler

open class AvtaleException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
