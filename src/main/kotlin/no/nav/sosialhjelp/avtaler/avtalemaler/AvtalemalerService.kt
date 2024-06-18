package no.nav.sosialhjelp.avtaler.avtalemaler

import no.nav.sosialhjelp.avtaler.Configuration
import no.nav.sosialhjelp.avtaler.avtaler.Avtale
import no.nav.sosialhjelp.avtaler.avtaler.AvtaleService
import no.nav.sosialhjelp.avtaler.db.DatabaseContext
import no.nav.sosialhjelp.avtaler.db.transaction
import no.nav.sosialhjelp.avtaler.gotenberg.GotenbergClient
import no.nav.sosialhjelp.avtaler.kommune.Kommune
import no.nav.sosialhjelp.avtaler.kommune.KommuneService
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class AvtalemalerService(
    private val databaseContext: DatabaseContext,
    private val kommuneService: KommuneService,
    private val avtaleService: AvtaleService,
    private val injectionService: InjectionService,
    private val gotenbergClient: GotenbergClient,
) {
    suspend fun lagreAvtalemal(avtale: Avtalemal): Avtalemal {
        return transaction(databaseContext) { ctx ->
            ctx.avtalemalerStore.lagreAvtalemal(avtale)
        }
    }

    suspend fun slettAvtalemal(uuid: UUID) =
        transaction(databaseContext) { ctx ->
            ctx.avtalemalerStore.slettAvtalemal(uuid)
        }

    suspend fun hentAvtalemaler(): List<Avtalemal> {
        return transaction(databaseContext) { ctx ->
            ctx.avtalemalerStore.hentAvtalemaler()
        }
    }

    suspend fun hentAvtalemal(uuid: UUID): Avtalemal? =
        transaction(databaseContext) { ctx ->
            ctx.avtalemalerStore.hentAvtalemal(uuid)
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
        val now = LocalDateTime.now()
        val nowString = now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        kommunerToPublish.filter { it.orgnr !in (alleredePublisert ?: emptyList()) }.forEach { kommune ->
            val replacements =
                avtalemal.replacementMap.mapValues {
                    when (it.value) {
                        Replacement.KOMMUNENAVN -> kommune.navn
                        Replacement.KOMMUNEORGNR -> kommune.orgnr
                        Replacement.DATO -> nowString
                    }
                }
            ByteArrayOutputStream().use {
                injectionService.injectReplacements(replacements, avtalemal.mal.inputStream(), it)
                val converted = gotenbergClient.convertToPdf("${avtalemal.navn}_${kommune.navn}.pdf", it.toByteArray())
                avtaleService.lagreAvtale(
                    Avtale(
                        uuid = UUID.randomUUID(),
                        orgnr = kommune.orgnr,
                        navn = "${avtalemal.navn}_${kommune.navn}",
                        opprettet = now,
                        avtaleversjon = "1.0",
                        erSignert = false,
                        navn_innsender = null,
                        avtalemal_uuid = avtalemal.uuid,
                    ),
                    converted,
                )
            }
        }

        val updated =
            avtalemal.copy(publisert = OffsetDateTime.now()).apply {
                mal = avtalemal.mal
                navn = avtalemal.navn
            }

        return transaction(databaseContext) { ctx ->
            ctx.avtalemalerStore.lagreAvtalemal(updated)
        }
    }
}
