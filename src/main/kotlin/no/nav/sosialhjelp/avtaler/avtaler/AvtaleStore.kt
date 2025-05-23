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

    fun lagreAvtaleDokument(
        uuid: UUID,
        dokument: ByteArray,
    )

    fun hentAvtaleDokument(uuid: UUID): ByteArray?

    fun hentAlle(): Map<UUID, List<String>>

    fun hentAlleForMal(uuid: UUID): List<Avtale>
}

data class Avtale(
    val uuid: UUID,
    val orgnr: String,
    val avtaleversjon: String? = null,
    val navn_innsender: String? = null,
    val erSignert: Boolean,
    val signert_tidspunkt: LocalDateTime? = null,
    val opprettet: LocalDateTime = LocalDateTime.now(),
    val navn: String,
    val avtalemal_uuid: UUID? = null,
)

class AvtaleStorePostgres(
    sessionFactory: () -> Session,
) : TransactionalStore(sessionFactory),
    AvtaleStore {
    override fun hentAvtalerForOrganisasjon(orgnr: String): List<Avtale> =
        session {
            @Language("PostgreSQL")
            val sql =
                """
                SELECT 
                    uuid,
                    orgnr,
                    avtaleversjon,
                    navn_innsender,
                    er_signert,
                    opprettet,
                    navn,
                    avtalemal_uuid,
                    signert_tidspunkt
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
                SELECT 
                    uuid,
                    orgnr,
                    avtaleversjon,
                    navn_innsender,
                    er_signert,
                    opprettet,
                    navn,
                    avtalemal_uuid,
                    signert_tidspunkt
                from avtale_v1
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
                    SELECT 
                        uuid,
                        orgnr,
                        avtaleversjon,
                        navn_innsender,
                        er_signert,
                        opprettet,
                        navn,
                        avtalemal_uuid,
                        signert_tidspunkt
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
                INSERT INTO avtale_v1 (
                                    uuid,
                                    orgnr,
                                    avtaleversjon,
                                    navn_innsender,
                                    er_signert,
                                    opprettet,
                                    navn,
                                    avtalemal_uuid,
                                    signert_tidspunkt
                                    )
                VALUES (:uuid, :orgnr, :avtaleversjon, :navn_innsender, :er_signert, :opprettet, :navn, :avtalemal_uuid, :signert_tidspunkt)
                ON CONFLICT on constraint avtale_v1_pkey do update set orgnr = :orgnr,
                                                                    avtaleversjon = :avtaleversjon,
                                                                    navn_innsender = :navn_innsender,
                                                                    er_signert = :er_signert,
                                                                    opprettet = :opprettet,
                                                                    navn = :navn,
                                                                    avtalemal_uuid = :avtalemal_uuid,
                                                                    signert_tidspunkt = :signert_tidspunkt
                """.trimIndent()
            it
                .update(
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
                        "signert_tidspunkt" to avtale.signert_tidspunkt,
                    ),
                ).validate()
            avtale
        }

    override fun lagreAvtaleDokument(
        uuid: UUID,
        dokument: ByteArray,
    ) = session {
        @Language("PostgreSQL")
        val sql =
            """
            update avtale_v1 set avtale = :dokument where uuid = :uuid
            """.trimIndent()
        it.update(sql, mapOf("dokument" to dokument, "uuid" to uuid)).validate()
    }

    override fun hentAvtaleDokument(uuid: UUID): ByteArray? =
        session {
            @Language("PostgreSQL")
            val sql =
                """
                select avtale from avtale_v1 where uuid = :uuid
                """.trimIndent()
            it.query(sql, mapOf("uuid" to uuid)) { row -> row.bytesOrNull("avtale") }
        }

    override fun hentAlle(): Map<UUID, List<String>> =
        session { session ->
            @Language("PostgreSQL")
            val sql =
                """
                select orgnr, avtalemal_uuid from avtale_v1
                """.trimIndent()
            session
                .queryList(sql, emptyMap()) { row ->
                    val uuidOrNull = row.uuidOrNull("avtalemal_uuid")
                    uuidOrNull?.let { it to row.string("orgnr") }
                }.groupBy { it.first }
                .mapValues { entry ->
                    entry.value.map { it.second }
                }
        }

    override fun hentAlleForMal(uuid: UUID): List<Avtale> =
        session { session ->
            @Language("PostgreSQL")
            val sql =
                """
                SELECT 
                    uuid,
                    orgnr,
                    avtaleversjon,
                    navn_innsender,
                    er_signert,
                    opprettet,
                    navn,
                    avtalemal_uuid,
                    signert_tidspunkt
                from avtale_v1
                where avtalemal_uuid = :uuid
                """.trimIndent()
            session.queryList(sql, mapOf("uuid" to uuid), ::mapper)
        }

    private fun mapper(row: Row): Avtale =
        Avtale(
            uuid = row.uuid("uuid"),
            orgnr = row.string("orgnr"),
            avtaleversjon = row.stringOrNull("avtaleversjon"),
            navn_innsender = row.stringOrNull("navn_innsender"),
            erSignert = row.boolean("er_signert"),
            opprettet = row.localDateTime("opprettet"),
            navn = row.string("navn"),
            avtalemal_uuid = row.uuidOrNull("avtalemal_uuid"),
            signert_tidspunkt = row.localDateTimeOrNull("signert_tidspunkt"),
        )
}
