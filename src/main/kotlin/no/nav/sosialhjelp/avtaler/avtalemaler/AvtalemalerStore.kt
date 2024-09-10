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
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

interface AvtalemalerStore : Store {
    fun hentAvtalemaler(): List<Avtalemal>

    fun hentAvtalemal(uuid: UUID): Avtalemal?

    fun lagreAvtalemal(avtale: Avtalemal): Avtalemal

    fun updatePublisert(
        uuid: UUID,
        publisert: OffsetDateTime,
    )

    fun slettAvtalemal(uuid: UUID)

    fun lagrePubliseringsjobb(publisering: Publisering): Publisering

    fun hentPubliseringer(avtalemalUuid: UUID): List<Publisering>

    fun hentFeiledePubliseringer(maxRetries: Int): List<Publisering>

    fun hentEksempel(uuid: UUID): ByteArray?

    fun hentSigneringsinfo(uuid: UUID): List<Signeringsinfo>
}

data class Publisering(
    val uuid: UUID,
    val orgnr: String,
    val avtalemalUuid: UUID,
    val retryCount: Int = 0,
    val avtaleUuid: UUID? = null,
)

enum class Replacement {
    KOMMUNENAVN,
    KOMMUNEORGNR,
    DATO,
}

data class Avtalemal(
    val uuid: UUID,
    val publisert: OffsetDateTime? = null,
    var ingress: String? = null,
    var ingressNynorsk: String? = null,
    var kvitteringstekst: String? = null,
    var kvitteringstekstNynorsk: String? = null,
    var replacementMap: Map<String, Replacement> = emptyMap(),
) {
    lateinit var examplePdf: ByteArray
    lateinit var mal: ByteArray
    lateinit var navn: String

    fun isInitialized() = this::mal.isInitialized && this::navn.isInitialized
}

class AvtalemalerStorePostgres(
    sessionFactory: () -> Session,
) : TransactionalStore(sessionFactory),
    AvtalemalerStore {
    override fun hentAvtalemaler(): List<Avtalemal> =
        session {
            val sql =
                """
                SELECT uuid, navn, mal, publisert, replacement_map, ingress, kvitteringstekst, ingress_nynorsk, kvitteringstekst_nynorsk from avtalemal
                """.trimIndent()
            it.queryList(sql, emptyList(), ::mapper)
        }

    override fun hentAvtalemal(uuid: UUID): Avtalemal? =
        session { session ->
            val sql =
                """
                SELECT uuid, navn, mal, publisert, replacement_map, ingress, kvitteringstekst, ingress_nynorsk, kvitteringstekst_nynorsk from avtalemal where uuid = :uuid
                """.trimIndent()
            session.query(sql, mapOf("uuid" to uuid), ::mapper)
        }

    override fun lagreAvtalemal(avtale: Avtalemal) =
        session { session ->
            val sql =
                """
                INSERT INTO avtalemal (uuid, navn, mal, publisert, replacement_map, ingress, kvitteringstekst, ingress_nynorsk, kvitteringstekst_nynorsk, example_pdf)
                VALUES (:uuid, :navn, :mal, :publisert, :replacement_map::jsonb, :ingress, :kvitteringstekst, :ingress_nynorsk, :kvitteringstekst_nynorsk, :example_pdf)
                ON CONFLICT ON CONSTRAINT avtalemal_pkey DO UPDATE SET navn = :navn, mal = :mal, publisert = :publisert, replacement_map = :replacement_map::jsonb, ingress = :ingress, kvitteringstekst = :kvitteringstekst, ingress_nynorsk = :ingress_nynorsk, kvitteringstekst_nynorsk = :kvitteringstekst_nynorsk, example_pdf = :example_pdf
                """.trimIndent()
            session
                .update(
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
                        "ingress" to avtale.ingress,
                        "ingress_nynorsk" to avtale.ingressNynorsk,
                        "kvitteringstekst" to avtale.kvitteringstekst,
                        "kvitteringstekst_nynorsk" to avtale.kvitteringstekstNynorsk,
                        "example_pdf" to avtale.examplePdf,
                    ),
                ).validate()
            avtale
        }

    override fun updatePublisert(
        uuid: UUID,
        publisert: OffsetDateTime,
    ) = session { session ->
        val sql = "update avtalemal set publisert = :publisert where uuid = :uuid"
        session
            .update(
                sql,
                mapOf(
                    "uuid" to uuid,
                    "publisert" to publisert,
                ),
            ).validate()
    }

    override fun slettAvtalemal(uuid: UUID) =
        session {
            val sql =
                """
                delete from avtalemal where uuid = :uuid
                """.trimIndent()
            it.update(sql, mapOf("uuid" to uuid)).validate()
        }

    override fun lagrePubliseringsjobb(publisering: Publisering) =
        session { session ->
            val sql =
                """
                insert into publisering (uuid, orgnr, avtalemal_uuid, avtale_uuid, retry_count)
                values (:uuid, :orgnr, :avtalemal_uuid, :avtale_uuid, :retry_count)
                on conflict on constraint publisering_pkey do update set orgnr = :orgnr, avtalemal_uuid = :avtalemal_uuid, avtale_uuid = :avtale_uuid, retry_count = :retry_count
                """.trimIndent()

            session
                .update(
                    sql,
                    mapOf(
                        "uuid" to publisering.uuid,
                        "orgnr" to publisering.orgnr,
                        "retry_count" to publisering.retryCount,
                        "avtalemal_uuid" to publisering.avtalemalUuid,
                        "avtale_uuid" to publisering.avtaleUuid,
                    ),
                ).validate()
            publisering
        }

    override fun hentPubliseringer(avtalemalUuid: UUID): List<Publisering> =
        session { session ->
            val sql =
                """
                select * from publisering where avtalemal_uuid = :avtalemal_uuid
                """.trimIndent()

            session.queryList(sql, mapOf("avtalemal_uuid" to avtalemalUuid)) {
                Publisering(
                    it.uuid("uuid"),
                    it.string("orgnr"),
                    it.uuid("avtalemal_uuid"),
                    it.int("retry_count"),
                    it.uuidOrNull("avtale_uuid"),
                )
            }
        }

    override fun hentFeiledePubliseringer(maxRetries: Int): List<Publisering> =
        session { session ->
            val sql =
                """
                select * from publisering where avtale_uuid is null and retry_count < :max_retries
                """.trimIndent()

            session.queryList(sql, mapOf("max_retries" to maxRetries)) {
                Publisering(
                    it.uuid("uuid"),
                    it.string("orgnr"),
                    it.uuid("avtalemal_uuid"),
                    it.int("retry_count"),
                    it.uuidOrNull("avtale_uuid"),
                )
            }
        }

    override fun hentEksempel(uuid: UUID): ByteArray? =
        session { session ->
            val sql =
                """
                select example_pdf from avtalemal where uuid = :uuid                    
                """.trimIndent()
            session.query(sql, mapOf("uuid" to uuid)) {
                it.bytes("example_pdf")
            }
        }

    override fun hentSigneringsinfo(uuid: UUID): List<Signeringsinfo> =
        session { session ->
            val sql =
                """
                select a.orgnr, a.er_signert, a.signert_tidspunkt from avtale_v1 a join digipost_jobb_data djd on a.uuid = djd.uuid where a.avtalemal_uuid = :uuid
                """.trimIndent()
            session.queryList(sql, mapOf("uuid" to uuid)) {
                Signeringsinfo(
                    it.string("orgnr"),
                    it.boolean("er_signert"),
                    it.localDateTimeOrNull("signert_tidspunkt"),
                )
            }
        }
}

data class Signeringsinfo(
    val orgnr: String,
    val erSignert: Boolean,
    val signertTidspunkt: LocalDateTime?,
)

private fun mapper(row: Row): Avtalemal =
    Avtalemal(
        row.uuid("uuid"),
        row.offsetDateTimeOrNull("publisert"),
        row.stringOrNull("ingress"),
        row.stringOrNull("ingress_nynorsk"),
        row.stringOrNull("kvitteringstekst"),
        row.stringOrNull("kvitteringstekst_nynorsk"),
        ObjectMapper()
            .readValue<Map<String, String>>(
                row.string("replacement_map"),
            ).mapValues { Replacement.valueOf(it.value) },
    ).apply {
        navn = row.string("navn")
        mal = row.bytes("mal")
    }
