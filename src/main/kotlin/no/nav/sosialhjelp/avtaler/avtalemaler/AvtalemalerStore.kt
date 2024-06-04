package no.nav.sosialhjelp.avtaler.avtalemaler

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import kotliquery.Session
import no.nav.sosialhjelp.avtaler.store.Store
import no.nav.sosialhjelp.avtaler.store.TransactionalStore
import no.nav.sosialhjelp.avtaler.store.query
import no.nav.sosialhjelp.avtaler.store.queryList
import no.nav.sosialhjelp.avtaler.store.update
import org.postgresql.util.PGobject
import java.time.OffsetDateTime
import java.util.UUID

interface AvtalemalerStore : Store {
    fun hentAvtalemaler(): List<Avtalemal>

    fun hentAvtalemal(uuid: UUID): Avtalemal?

    fun lagreAvtalemal(avtale: Avtalemal): Avtalemal

    fun slettAvtalemal(uuid: UUID)
}

enum class Replacement {
    KOMMUNENAVN,
    KOMMUNEORGNR,
    DATO,
}

data class Avtalemal(
    val uuid: UUID,
    val publisert: OffsetDateTime? = null,
    var replacementMap: Map<String, Replacement> = emptyMap(),
) {
    lateinit var mal: ByteArray
    lateinit var navn: String

    fun isInitialized() = this::mal.isInitialized && this::navn.isInitialized

    fun copy() =
        Avtalemal(uuid, publisert, replacementMap).let {
            it.mal = mal
            it.navn = navn
        }
}

class AvtalemalerStorePostgres(sessionFactory: () -> Session) : AvtalemalerStore, TransactionalStore(sessionFactory) {
    override fun hentAvtalemaler(): List<Avtalemal> =
        session {
            val sql =
                """
                SELECT uuid, navn, mal, publisert, replacement_map from avtalemal
                """.trimIndent()
            it.queryList(sql, emptyList(), ::mapper)
        }

    override fun hentAvtalemal(uuid: UUID): Avtalemal? =
        session { session ->
            val sql =
                """
                SELECT uuid, navn, mal, publisert, replacement_map from avtalemal where uuid = :uuid
                """.trimIndent()
            session.query(sql, mapOf("uuid" to uuid), ::mapper)
        }

    override fun lagreAvtalemal(avtale: Avtalemal) =
        session { session ->
            val sql =
                """
                INSERT INTO avtalemal (uuid, navn, mal, publisert, replacement_map)
                VALUES (:uuid, :navn, :mal, :publisert, :replacement_map::jsonb)
                ON CONFLICT ON CONSTRAINT avtalemal_pkey DO UPDATE SET navn = :navn, mal = :mal, publisert = :publisert, replacement_map = :replacement_map::jsonb
                """.trimIndent()
            session.update(
                sql,
                mapOf(
                    "uuid" to avtale.uuid,
                    "navn" to avtale.navn,
                    "mal" to avtale.mal,
                    "publisert" to avtale.publisert,
                    "replacement_map" to
                        PGobject().apply {
                            type = "jsonb"
                            value = ObjectMapper().writeValueAsString(avtale.replacementMap.mapValues { it.value.name })
                        },
                ),
            ).validate()
            avtale
        }

    override fun slettAvtalemal(uuid: UUID) =
        session {
            val sql =
                """
                delete from avtalemal where uuid = :uuid
                """.trimIndent()
            it.update(sql, mapOf("uuid" to uuid)).validate()
        }
}

private fun mapper(row: Row): Avtalemal =
    Avtalemal(
        row.uuid("uuid"),
        row.offsetDateTimeOrNull("publisert"),
        ObjectMapper().readValue<Map<String, String>>(
            row.string("replacement_map"),
        ).mapValues { Replacement.valueOf(it.value) },
    ).apply {
        navn = row.string("navn")
        mal = row.bytes("mal")
    }
