package no.nav.sosialhjelp.avtaler.exceptions

class VirsomhetsertifikatException(
    override val message: String?,
    override val cause: Throwable?,
) : RuntimeException(message, cause)
