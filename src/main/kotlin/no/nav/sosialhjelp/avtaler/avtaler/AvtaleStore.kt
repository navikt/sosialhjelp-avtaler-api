package no.nav.sosialhjelp.avtaler.avtaler

import kotliquery.Row
import kotliquery.Session
import no.nav.sosialhjelp.avtaler.store.Store
import no.nav.sosialhjelp.avtaler.store.TransactionalStore
import no.nav.sosialhjelp.avtaler.store.query
import no.nav.sosialhjelp.avtaler.store.queryList
import no.nav.sosialhjelp.avtaler.store.update
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime
import java.util.UUID

interface AvtaleStore : Store {
    fun hentAvtalerForOrganisasjon(orgnr: String): List<Avtale>

    fun hentAvtale(uuid: UUID): Avtale?

    fun hentAvtalerForOrganisasjoner(orgnr: List<String>): List<Avtale>

    fun lagreAvtale(avtale: Avtale): Avtale
}

data class Avtale(
    val uuid: UUID,
    val orgnr: String,
    val avtaleversjon: String? = null,
    val navn_innsender: String,
    val erSignert: Boolean,
    val opprettet: LocalDateTime = LocalDateTime.now(),
    val navn: String,
    val avtalemal_uuid: UUID? = null,
)

class AvtaleStorePostgres(sessionFactory: () -> Session) : AvtaleStore,
    TransactionalStore(sessionFactory) {
    override fun hentAvtalerForOrganisasjon(orgnr: String): List<Avtale> =
        session {
            @Language("PostgreSQL")
            val sql =
                """
                SELECT uuid, orgnr, avtaleversjon, navn_innsender, er_signert, opprettet
                FROM avtale_v1
                WHERE orgnr = :orgnr
                """.trimIndent()
            it.queryList(sql, mapOf("orgnr" to orgnr), ::mapper)
        }

    override fun hentAvtale(uuid: UUID): Avtale? =
        session { session ->
            @Language("PostgreSQL")
            val sql =
                """
                SELECT * from postgres.public.avtale_v1
                where uuid = :uuid
                """.trimIndent()
            session.query(sql, mapOf("uuid" to uuid), ::mapper)
        }

    override fun hentAvtalerForOrganisasjoner(orgnr: List<String>): List<Avtale> =
        session { session ->
            if (orgnr.isEmpty()) {
                emptyList()
            } else {
                @Language("PostgreSQL")
                var sql =
                    """
                    SELECT uuid, orgnr, avtaleversjon, navn_innsender, er_signert, opprettet
                    FROM avtale_v1
                    WHERE orgnr in (?)
                    """.trimIndent()
                sql = sql.replace("(?)", "(" + (0 until orgnr.count()).joinToString { "?" } + ")")
                session.queryList(sql, orgnr, ::mapper)
            }
        }

    override fun lagreAvtale(avtale: Avtale): Avtale =
        session {
            @Language("PostgreSQL")
            val sql =
                """
                INSERT INTO avtale_v1 (uuid,
                                        orgnr,
                                       avtaleversjon,
                                       navn_innsender,
                                       er_signert,
                                       opprettet,
                                        navn,
                                        avtalemal_uuid
                                       )
                VALUES (:orgnr, :avtaleversjon, :navn_innsender, :er_signert, :opprettet, :navn, :avtalemal_uuid)
                ON CONFLICT on constraint avtale_v1_pkey do update set orgnr = :orgnr,
                                                                    avtaleversjon = :avtaleversjon,
                                                                    navn_innsender = :navn_innsender,
                                                                    er_signert = :er_signert,
                                                                    opprettet = :opprettet,
                                                                    navn = :navn,
                                                                    avtalemal_uuid = :avtalemal_uuid
                """.trimIndent()
            it.update(
                sql,
                mapOf(
                    "uuid" to avtale.uuid,
                    "orgnr" to avtale.orgnr,
                    "avtaleversjon" to avtale.avtaleversjon,
                    "navn_innsender" to avtale.navn_innsender,
                    "er_signert" to avtale.erSignert,
                    "opprettet" to avtale.opprettet,
                    "navn" to avtale.navn,
                    "avtalemal_uuid" to avtale.avtalemal_uuid,
                ),
            ).validate()
            avtale
        }

    private fun mapper(row: Row): Avtale =
        Avtale(
            uuid = row.uuid("uuid"),
            orgnr = row.string("orgnr"),
            avtaleversjon = row.stringOrNull("avtaleversjon"),
            navn_innsender = row.string("navn_innsender"),
            erSignert = row.boolean("er_signert"),
            opprettet = row.localDateTime("opprettet"),
            navn = row.string("navn"),
            avtalemal_uuid = row.uuidOrNull("avtalemal_uuid"),
        )
}
