package no.nav.sosialhjelp.avtaler.avtalemaler

import kotliquery.Session
import no.nav.sosialhjelp.avtaler.store.Store
import no.nav.sosialhjelp.avtaler.store.TransactionalStore
import no.nav.sosialhjelp.avtaler.store.query
import no.nav.sosialhjelp.avtaler.store.queryList
import no.nav.sosialhjelp.avtaler.store.update
import java.time.OffsetDateTime
import java.util.UUID

interface AvtalemalerStore : Store {
    fun hentAvtalemaler(): List<Avtalemal>

    fun hentAvtalemal(uuid: UUID): Avtalemal?

    fun lagreAvtalemal(avtale: Avtalemal): Avtalemal

    fun slettAvtalemal(uuid: UUID)
}

data class Avtalemal(
    val uuid: UUID,
    var navn: String? = null,
    var mal: ByteArray? = null,
    val publisert: OffsetDateTime? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Avtalemal

        if (uuid != other.uuid) return false
        if (navn != other.navn) return false
        if (!mal.contentEquals(other.mal)) return false
        if (publisert != other.publisert) return false

        return true
    }

    override fun hashCode(): Int {
        var result = uuid.hashCode()
        result = 31 * result + navn.hashCode()
        result = 31 * result + mal.contentHashCode()
        result = 31 * result + publisert.hashCode()
        return result
    }
}

class AvtalemalerStorePostgres(sessionFactory: () -> Session) : AvtalemalerStore, TransactionalStore(sessionFactory) {
    override fun hentAvtalemaler(): List<Avtalemal> =
        session {
            val sql =
                """
                SELECT uuid, navn, mal, publisert from avtalemal
                """.trimIndent()
            it.queryList(sql, emptyList()) { row ->
                Avtalemal(row.uuid("uuid"), row.stringOrNull("navn"), row.bytesOrNull("mal"), row.offsetDateTimeOrNull("publisert"))
            }
        }

    override fun hentAvtalemal(uuid: UUID): Avtalemal? =
        session {
            val sql =
                """
                SELECT uuid, navn, mal, publisert from avtalemal where uuid = :uuid
                """.trimIndent()
            it.query(sql, mapOf("uuid" to uuid)) { row ->
                Avtalemal(row.uuid("uuid"), row.stringOrNull("navn"), row.bytesOrNull("mal"), row.offsetDateTimeOrNull("publisert"))
            }
        }

    override fun lagreAvtalemal(avtale: Avtalemal) =
        session {
            val sql =
                """
                INSERT INTO avtalemal (uuid, navn, mal, publisert)
                VALUES (:uuid, :navn, :mal, :publisert)
                ON CONFLICT ON CONSTRAINT avtalemal_pkey DO UPDATE SET navn = :navn, mal = :mal, publisert = :publisert
                """.trimIndent()
            it.update(
                sql,
                mapOf(
                    "uuid" to avtale.uuid,
                    "navn" to avtale.navn,
                    "mal" to avtale.mal,
                    "publisert" to avtale.publisert,
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
