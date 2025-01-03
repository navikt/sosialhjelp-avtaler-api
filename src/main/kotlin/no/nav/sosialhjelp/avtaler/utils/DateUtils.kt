package no.nav.sosialhjelp.avtaler.utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun LocalDateTime?.format(): String =
    this?.format(
        DateTimeFormatter.ofPattern("dd.MM.yyyy"),
    ) ?: "datoløs"
