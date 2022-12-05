package no.nav.sosialhjelp.avtaler.digipost

import kotliquery.Row
import kotliquery.Session
import no.nav.sosialhjelp.avtaler.store.Store
import no.nav.sosialhjelp.avtaler.store.TransactionalStore
import no.nav.sosialhjelp.avtaler.store.query
import no.nav.sosialhjelp.avtaler.store.update
import org.intellij.lang.annotations.Language
import java.net.URI

interface DigipostJobbDataStore : Store {
    fun lagreDigipostResponse(digipostJobbData: DigipostJobbData): DigipostJobbData

    fun hentDigipostJobb(orgnr: String): DigipostJobbData?
}

class DigipostJobbDataStorePostgres(private val sessionFactory: () -> Session) : DigipostJobbDataStore, TransactionalStore(sessionFactory) {
    override fun lagreDigipostResponse(digipostJobbData: DigipostJobbData): DigipostJobbData = session {
        @Language("PostgreSQL")
        val sql = """
            INSERT INTO digipost_jobb_data (orgnr,
                                            direct_job_reference,
                                            status_url)
            VALUES (:orgnr, :direct_job_reference, :status_url)
            ON CONFLICT (orgnr) 
            DO UPDATE SET direct_job_reference = :direct_job_reference, status_url = :status_url
        """.trimIndent()

        it.update(
            sql,
            mapOf(
                "orgnr" to digipostJobbData.orgnr,
                "direct_job_reference" to digipostJobbData.directJobReference,
                "status_url" to digipostJobbData.statusUrl.toString()
            )
        )
        digipostJobbData
    }

    override fun hentDigipostJobb(orgnr: String): DigipostJobbData? = session {
        @Language("PostgreSQL")
        val sql = """
            SELECT * 
            FROM digipost_jobb_data 
            WHERE orgnr = :orgnr
        """.trimIndent()
        it.query(sql, mapOf("orgnr" to orgnr), ::mapper)
    }

    private fun mapper(row: Row): DigipostJobbData = DigipostJobbData(
        orgnr = row.string("orgnr"),
        directJobReference = row.string("direct_job_reference"),
        statusUrl = URI(row.string("status_url"))
    )
}
