package no.nav.sosialhjelp.avtaler.digipost

import kotliquery.Row
import kotliquery.Session
import no.nav.sosialhjelp.avtaler.store.Store
import no.nav.sosialhjelp.avtaler.store.TransactionalStore
import no.nav.sosialhjelp.avtaler.store.query
import no.nav.sosialhjelp.avtaler.store.queryList
import no.nav.sosialhjelp.avtaler.store.update
import org.intellij.lang.annotations.Language
import java.net.URI
import java.util.UUID

interface DigipostJobbDataStore : Store {
    fun lagreDigipostResponse(digipostJobbData: DigipostJobbData): DigipostJobbData

    fun hentDigipostJobb(uuid: UUID): DigipostJobbData?

    fun oppdaterDigipostJobbData(digipostJobbData: DigipostJobbData): DigipostJobbData

    fun hentAlleUtenLagretDokument(): List<DigipostJobbData>
}

class DigipostJobbDataStorePostgres(
    sessionFactory: () -> Session,
) : TransactionalStore(sessionFactory),
    DigipostJobbDataStore {
    override fun lagreDigipostResponse(digipostJobbData: DigipostJobbData): DigipostJobbData =
        session {
            @Language("PostgreSQL")
            val sql =
                """
                INSERT INTO digipost_jobb_data (uuid,
                                                direct_job_reference,
                                                status_url,
                                                status_query_token,
                                                signert_dokument)
                VALUES (:uuid, :direct_job_reference, :status_url, :status_query_token, :signert_dokument)
                ON CONFLICT (uuid) 
                DO UPDATE SET direct_job_reference = :direct_job_reference, status_url = :status_url, status_query_token = :status_query_token, signert_dokument = :signert_dokument
                """.trimIndent()

            it.update(
                sql,
                mapOf(
                    "uuid" to digipostJobbData.uuid,
                    "direct_job_reference" to digipostJobbData.directJobReference,
                    "status_url" to digipostJobbData.statusUrl.toString(),
                    "status_query_token" to digipostJobbData.statusQueryToken,
                    "signert_dokument" to digipostJobbData.signertDokument,
                ),
            )
            digipostJobbData
        }

    override fun hentDigipostJobb(uuid: UUID): DigipostJobbData? =
        session {
            @Language("PostgreSQL")
            val sql =
                """
                SELECT * 
                FROM digipost_jobb_data 
                WHERE uuid = :uuid
                """.trimIndent()
            it.query(sql, mapOf("uuid" to uuid), ::mapper)
        }

    override fun oppdaterDigipostJobbData(digipostJobbData: DigipostJobbData): DigipostJobbData =
        session {
            @Language("PostgreSQL")
            val sql =
                """
                UPDATE digipost_jobb_data 
                SET status_query_token = :status_query_token, signert_dokument = :signert_dokument
                WHERE uuid = :uuid
                """.trimIndent()
            it.update(
                sql,
                mapOf(
                    "uuid" to digipostJobbData.uuid,
                    "status_query_token" to digipostJobbData.statusQueryToken,
                    "signert_dokument" to digipostJobbData.signertDokument,
                ),
            )
            digipostJobbData
        }

    override fun hentAlleUtenLagretDokument(): List<DigipostJobbData> =
        session {
            @Language("PostgreSQL")
            val sql =
                """
                SELECT * 
                FROM digipost_jobb_data 
                WHERE signert_dokument IS NULL AND status_query_token IS NOT NULL
                """.trimIndent()
            it.queryList(sql, mapOf(), ::mapper)
        }

    private fun mapper(row: Row): DigipostJobbData =
        DigipostJobbData(
            uuid = row.uuid("uuid"),
            directJobReference = row.string("direct_job_reference"),
            statusUrl = URI(row.string("status_url")),
            statusQueryToken = row.stringOrNull("status_query_token"),
            signertDokument = row.binaryStreamOrNull("signert_dokument"),
        )
}
