package no.nav.sosialhjelp.avtaler.avtalemaler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.newFixedThreadPoolContext
import no.nav.sosialhjelp.avtaler.Configuration
import no.nav.sosialhjelp.avtaler.avtaler.Avtale
import no.nav.sosialhjelp.avtaler.avtaler.AvtaleService
import no.nav.sosialhjelp.avtaler.db.DatabaseContext
import no.nav.sosialhjelp.avtaler.db.transaction
import no.nav.sosialhjelp.avtaler.ereg.EregClient
import no.nav.sosialhjelp.avtaler.gotenberg.GotenbergClient
import no.nav.sosialhjelp.avtaler.kommune.Kommune
import no.nav.sosialhjelp.avtaler.kommune.KommuneService
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

val pdfConvertContext = newFixedThreadPoolContext(10, "pdfConvert")

class AvtalemalerService(
    private val databaseContext: DatabaseContext,
    private val kommuneService: KommuneService,
    private val avtaleService: AvtaleService,
    private val injectionService: InjectionService,
    private val gotenbergClient: GotenbergClient,
    private val eregClient: EregClient,
) {
    suspend fun lagreAvtalemal(avtale: Avtalemal): Avtalemal =
        transaction(databaseContext) { ctx ->
            ctx.avtalemalerStore.lagreAvtalemal(avtale)
        }

    suspend fun slettAvtalemal(uuid: UUID) =
        transaction(databaseContext) { ctx ->
            ctx.avtalemalerStore.slettAvtalemal(uuid)
        }

    suspend fun hentAvtalemaler(): List<Avtalemal> =
        transaction(databaseContext) { ctx ->
            ctx.avtalemalerStore.hentAvtalemaler()
        }

    suspend fun hentAvtalemal(uuid: UUID): Avtalemal? =
        transaction(databaseContext) { ctx ->
            ctx.avtalemalerStore.hentAvtalemal(uuid)
        }

    suspend fun hentPubliseringer(avtalemalUUID: UUID): List<Publisering> =
        transaction(databaseContext) { ctx ->
            ctx.avtalemalerStore.hentPubliseringer(avtalemalUUID)
        }

    suspend fun publiser(
        uuid: UUID,
        kommuner: List<String>?,
    ): Avtalemal? {
        val alleredePublisert = avtaleService.hentAvtalemalToOrgnrMap()[uuid]
        val alleKommuner = kommuneService.getAlleKommuner()
        val kommunerToPublish =
            kommuner?.takeUnless { it.isEmpty() }?.mapNotNull { kommune ->
                alleKommuner.find { it.orgnr == kommune } ?: if (!Configuration.prod) {
                    Kommune(kommune, "Ukjent")
                } else {
                    null
                }
            } ?: alleKommuner

        val avtalemal =
            transaction(databaseContext) { ctx ->
                ctx.avtalemalerStore.hentAvtalemal(uuid)
            } ?: return null
        val publiseringer =
            kommunerToPublish
                .filter {
                    it.orgnr !in (alleredePublisert ?: emptyList())
                }.map { kommune ->
                    val publisering = Publisering(UUID.randomUUID(), kommune.orgnr, uuid)
                    transaction(databaseContext) { ctx ->
                        ctx.avtalemalerStore.lagrePubliseringsjobb(publisering)
                    }
                }

        initiatePublisering(publiseringer)

        val updated =
            avtalemal.copy(publisert = OffsetDateTime.now()).apply {
                mal = avtalemal.mal
                navn = avtalemal.navn
            }

        return transaction(databaseContext) { ctx ->
            ctx.avtalemalerStore.lagreAvtalemal(updated)
        }
    }

    suspend fun hentFeiledePubliseringer(): List<Publisering> =
        transaction(databaseContext) { ctx ->
            ctx.avtalemalerStore.hentFeiledePubliseringer(5)
        }

    fun initiatePublisering(publiseringer: List<Publisering>) {
        val scope = CoroutineScope(Dispatchers.IO)
        flowOf(*publiseringer.toTypedArray())
            .onEach { publisering ->
                runCatching { processPublisering(publisering) }.onFailure {
                    log.error(it) { "Feil ved publisering" }
                    transaction(databaseContext) { ctx ->
                        ctx.avtalemalerStore.lagrePubliseringsjobb(publisering.copy(retryCount = publisering.retryCount + 1))
                    }
                }
            }.onCompletion { cause ->
                if (cause == null) {
                    log.info { "Publisering ferdig" }
                } else {
                    log.error(cause) { "Publisering avbrutt" }
                }
            }.launchIn(scope)
    }

    private suspend fun processPublisering(publisering: Publisering) {
        val avtalemal =
            transaction(databaseContext) { ctx ->
                ctx.avtalemalerStore.hentAvtalemal(publisering.avtalemalUuid)
            } ?: return
        val kommuneNavn = eregClient.hentEnhetNavn(publisering.orgnr)
        val now = LocalDateTime.now()
        val nowString = now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        val replacements =
            avtalemal.replacementMap.mapValues {
                when (it.value) {
                    Replacement.KOMMUNENAVN -> kommuneNavn
                    Replacement.KOMMUNEORGNR -> publisering.orgnr
                    Replacement.DATO -> nowString
                }
            }
        val converted =
            ByteArrayOutputStream().use {
                injectionService.injectReplacements(replacements, avtalemal.mal.inputStream(), it)
                gotenbergClient.convertToPdf("${avtalemal.navn}.pdf", it.toByteArray())
            }
        val avtale =
            avtaleService.lagreAvtale(
                Avtale(
                    uuid = UUID.randomUUID(),
                    orgnr = publisering.orgnr,
                    navn = "${avtalemal.navn}_$kommuneNavn",
                    opprettet = now,
                    avtaleversjon = "1.0",
                    erSignert = false,
                    navn_innsender = null,
                    avtalemal_uuid = avtalemal.uuid,
                ),
                converted,
            )
        transaction(databaseContext) { ctx ->
            ctx.avtalemalerStore.lagrePubliseringsjobb(publisering.copy(avtaleUuid = avtale.uuid))
        }
    }
}
