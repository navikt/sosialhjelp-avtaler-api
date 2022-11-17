package no.nav.sosialhjelp.avtaler.avtaler

import kotliquery.Row
import kotliquery.Session
import mu.KotlinLogging
import no.nav.sosialhjelp.avtaler.store.Store
import no.nav.sosialhjelp.avtaler.store.TransactionalStore
import no.nav.sosialhjelp.avtaler.store.query
import no.nav.sosialhjelp.avtaler.store.queryList
import no.nav.sosialhjelp.avtaler.store.update
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

interface AvtaleStore : Store {
    fun hentAvtaleForOrganisasjon(orgnr: String): Avtale?
    fun hentAvtalerForOrganisasjoner(orgnr: List<String>): List<Avtale>
    fun lagreAvtale(avtale: Avtale): Avtale
}

data class Avtale(
    val orgnr: String,
    val avtaleversjon: String? = null,
    val navn_innsender: String,
    val opprettet: LocalDateTime = LocalDateTime.now()
)

class AvtaleStorePostgres(private val sessionFactory: () -> Session) : AvtaleStore,
    TransactionalStore(sessionFactory) {

    override fun hentAvtaleForOrganisasjon(orgnr: String): Avtale? = session {
        @Language("PostgreSQL")
        val sql = """
            SELECT orgnr, avtaleversjon, navn_innsender, opprettet
            FROM avtale_v1
            WHERE orgnr = :orgnr
        """.trimIndent()
        it.query(sql, mapOf("orgnr" to orgnr), ::mapper)
    }

    override fun hentAvtalerForOrganisasjoner(orgnr: List<String>): List<Avtale> = session {
        if (orgnr.isEmpty()) {
            emptyList()
        } else {
            @Language("PostgreSQL")
            var sql = """
            SELECT orgnr, avtaleversjon, navn_innsender, opprettet
            FROM avtale_v1
            WHERE orgnr in (?)
            """.trimIndent()
            sql = sql.replace("(?)", "(" + (0 until orgnr.count()).joinToString { "?" } + ")")
            it.queryList(sql, orgnr, ::mapper)
        }
    }

    override fun lagreAvtale(avtale: Avtale): Avtale = session {

        @Language("PostgreSQL")
        val sql = """
            INSERT INTO avtale_v1 (orgnr,
                                   avtaleversjon,
                                   navn_innsender,
                                   opprettet
                                   )
            VALUES (:orgnr, :avtaleversjon, :navn_innsender, :opprettet)
            ON CONFLICT DO NOTHING
        """.trimIndent()
        it.update(
            sql,
            mapOf(
                "orgnr" to avtale.orgnr,
                "avtaleversjon" to avtale.avtaleversjon,
                "navn_innsender" to avtale.navn_innsender,
                "opprettet" to avtale.opprettet
            )
        ).validate()
        avtale
    }

    private fun mapper(row: Row): Avtale = Avtale(
        orgnr = row.string("orgnr"),
        avtaleversjon = row.stringOrNull("avtaleversjon"),
        navn_innsender = row.string("navn_innsender"),
        opprettet = row.localDateTime("opprettet"),
    )
}
