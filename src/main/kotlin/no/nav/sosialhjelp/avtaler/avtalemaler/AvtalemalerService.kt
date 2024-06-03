package no.nav.sosialhjelp.avtaler.avtalemaler

import no.nav.sosialhjelp.avtaler.db.DatabaseContext
import no.nav.sosialhjelp.avtaler.db.transaction
import java.time.OffsetDateTime
import java.util.UUID

class AvtalemalerService(private val databaseContext: DatabaseContext) {
    suspend fun lagreAvtalemal(avtale: Avtalemal): Avtalemal {
        requireNotNull(avtale.mal)
        requireNotNull(avtale.navn)
        return transaction(databaseContext) { ctx ->
            ctx.avtalemalerStore.lagreAvtalemal(avtale)
        }
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

    suspend fun publiser(uuid: UUID): Avtalemal? =
        transaction(databaseContext) { ctx ->
            val avtalemal = ctx.avtalemalerStore.hentAvtalemal(uuid) ?: return@transaction null
            requireNotNull(avtalemal.mal)
            val updated = avtalemal.copy(publisert = OffsetDateTime.now())
            ctx.avtalemalerStore.lagreAvtalemal(updated)
            // Lag entry for alle kommuner / subset av kommuner
        }
}
