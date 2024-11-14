package no.nav.sosialhjelp.avtaler.digipost

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import mu.KotlinLogging
import no.digipost.signature.client.ClientConfiguration
import no.digipost.signature.client.ServiceEnvironment
import no.digipost.signature.client.core.ConfirmationReference
import no.digipost.signature.client.core.DeleteDocumentsUrl
import no.digipost.signature.client.core.DocumentType
import no.digipost.signature.client.core.IdentifierInSignedDocuments
import no.digipost.signature.client.core.PAdESReference
import no.digipost.signature.client.core.ResponseInputStream
import no.digipost.signature.client.core.Sender
import no.digipost.signature.client.direct.DirectClient
import no.digipost.signature.client.direct.DirectDocument
import no.digipost.signature.client.direct.DirectJob
import no.digipost.signature.client.direct.DirectJobResponse
import no.digipost.signature.client.direct.DirectJobStatus
import no.digipost.signature.client.direct.DirectJobStatusResponse
import no.digipost.signature.client.direct.DirectSigner
import no.digipost.signature.client.direct.ExitUrls
import no.digipost.signature.client.direct.Signature
import no.digipost.signature.client.direct.SignerStatus
import no.digipost.signature.client.direct.StatusReference
import no.digipost.signature.client.security.KeyStoreConfig
import no.nav.sosialhjelp.avtaler.Configuration
import no.nav.sosialhjelp.avtaler.avtaler.Avtale
import no.nav.sosialhjelp.avtaler.exceptions.VirsomhetsertifikatException
import no.nav.sosialhjelp.avtaler.secretmanager.DigisosKeyStoreCredentials
import no.nav.sosialhjelp.avtaler.secretmanager.SecretManager
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URI
import java.time.Instant
import java.util.Collections
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private val log = KotlinLogging.logger {}

data class DigipostResponse(
    val redirectUrl: URI,
    val signerUrl: URI,
    val reference: String,
)

interface DigipostClient {
    fun sendTilSignering(
        fnr: String,
        avtale: Avtale,
        dokument: ByteArray,
        navn: String,
    ): DigipostResponse

    fun getDirectJobStatus(
        directJobReference: String,
        statusUrl: URI,
        statusQueryToken: String,
    ): DirectJobStatusResponse

    fun hentSignertAvtale(
        statusQueryToken: String,
        jobReference: String,
        statusUrl: URI,
    ): ResponseInputStream?
}

class DigipostClientImpl(
    props: Configuration.DigipostProperties,
    virksomhetProps: Configuration.VirksomhetssertifikatProperties,
    profile: Configuration.Profile,
) : DigipostClient {
    private val accessSecretVersion: SecretManager = SecretManager
    private val onCompletionUrl = props.onCompletionUrl
    private val onErrorUrl = props.onErrorUrl
    private val onRejectionUrl = props.onRejectionUrl
    private val virksomhetPasswordProjectId = virksomhetProps.passwordProjectId
    private val virksomhetPasswordSecretId = virksomhetProps.passwordSecretId
    private val virksomhetPasswordVersionId = virksomhetProps.passwordSecretVersionId
    private val virksomhetProjectId = virksomhetProps.projectId
    private val virksomhetSecretId = virksomhetProps.secretId
    private val virksomhetVersionId = virksomhetProps.versionId
    private val keyStoreConfig: KeyStoreConfig = configure(accessSecretVersion)
    private val clientConfiguration =
        ClientConfiguration
            .builder(keyStoreConfig)
            .serviceEnvironment(if (profile == Configuration.Profile.PROD) ServiceEnvironment.PRODUCTION else ServiceEnvironment.DIFITEST)
            .defaultSender(Sender(props.navOrgnr))
            .timeoutsForDocumentDownloads {
                it.allTimeouts(30.seconds.toJavaDuration())
            }.build()
    private val client = DirectClient(clientConfiguration)

    private fun configure(accessSecretVersion: SecretManager): KeyStoreConfig {
        val certificatePassword =
            accessSecretVersion
                .accessSecretVersion(
                    virksomhetPasswordProjectId,
                    virksomhetPasswordSecretId,
                    virksomhetPasswordVersionId,
                )?.data
                ?.toStringUtf8()
        val objectMapper = ObjectMapper().registerKotlinModule()
        val keystoreCredentials: DigisosKeyStoreCredentials =
            objectMapper.readValue(
                certificatePassword,
                DigisosKeyStoreCredentials::class.java,
            )

        val secretPayload = accessSecretVersion.accessSecretVersion(virksomhetProjectId, virksomhetSecretId, virksomhetVersionId)

        val inputStream =
            try {
                ByteArrayInputStream(secretPayload!!.data.toByteArray())
            } catch (e: Exception) {
                log.error("Kunne ikke hente virksomhetssertifikat. SecretPayload er null.")
                throw VirsomhetsertifikatException("Kunne ikke hente virksomhetssertifikat. SecretPayload er null.", e)
            }

        log.info("Hentet sertifikat med lengde: ${secretPayload.data.size()}")

        return KeyStoreConfig.fromJavaKeyStore(
            inputStream,
            keystoreCredentials.alias,
            keystoreCredentials.password,
            keystoreCredentials.password,
        )
    }

    override fun sendTilSignering(
        fnr: String,
        avtale: Avtale,
        dokument: ByteArray,
        navn: String,
    ): DigipostResponse {
        val exitUrls =
            ExitUrls.of(
                URI.create(onCompletionUrl + avtale.uuid),
                URI.create(onRejectionUrl + avtale.uuid),
                URI.create(onErrorUrl + avtale.uuid),
            )

        val avtaleTittel = avtale.navn
        val documents: List<DirectDocument> =
            listOf(
                DirectDocument.builder(avtaleTittel, dokument).type(DocumentType.PDF).build(),
            )

        val signers: List<DirectSigner> =
            Collections.singletonList(
                DirectSigner
                    .withPersonalIdentificationNumber(fnr)
                    .build(),
            )

        val job =
            DirectJob
                .builder(avtaleTittel, documents, signers, exitUrls)
                .withReference(avtale.uuid)
                .withIdentifierInSignedDocuments(IdentifierInSignedDocuments.NAME)
                .build()

        val directJobResponse = client.create(job)
        if (directJobResponse.singleSigner.redirectUrl == null) {
            log.error("Kan ikke redirecte bruker til e-signering. Redirect URL fra Digipost er null.")
            throw DigipostException("Redirect URL fra Digipost er null.")
        }
        return DigipostResponse(directJobResponse.singleSigner.redirectUrl, directJobResponse.statusUrl, directJobResponse.reference)
    }

    override fun getDirectJobStatus(
        directJobReference: String,
        statusUrl: URI,
        statusQueryToken: String,
    ): DirectJobStatusResponse {
        val directJobResponse = DirectJobResponse(1, directJobReference, statusUrl, null)
        val directJobStatusResponse =
            client.getStatus(
                StatusReference
                    .of(directJobResponse)
                    .withStatusQueryToken(statusQueryToken),
            )
        return directJobStatusResponse
    }

    override fun hentSignertAvtale(
        statusQueryToken: String,
        jobReference: String,
        statusUrl: URI,
    ): ResponseInputStream? {
        val directJobResponse = DirectJobResponse(1, jobReference, statusUrl, null)
        val directJobStatusResponse = client.getStatus(StatusReference.of(directJobResponse).withStatusQueryToken(statusQueryToken))

        return if (directJobStatusResponse.isPAdESAvailable) client.getPAdES(directJobStatusResponse.getpAdESUrl()) else null
    }
}

class DigipostClientLocal : DigipostClient {
    override fun sendTilSignering(
        fnr: String,
        avtale: Avtale,
        dokument: ByteArray,
        navn: String,
    ): DigipostResponse = DigipostResponse(URI.create("http://localhost:8080"), URI.create("http://localhost:8080"), "1234")

    override fun getDirectJobStatus(
        directJobReference: String,
        statusUrl: URI,
        statusQueryToken: String,
    ): DirectJobStatusResponse =
        DirectJobStatusResponse(
            1L,
            "ref",
            DirectJobStatus.COMPLETED_SUCCESSFULLY,
            ConfirmationReference.of(URI.create("")),
            DeleteDocumentsUrl.of(URI.create("abc")),
            listOf(Signature("Some Guy", SignerStatus.SIGNED, Instant.now(), null)),
            PAdESReference.of(URI.create("")),
            Instant.now().plusSeconds(10),
        )

    override fun hentSignertAvtale(
        statusQueryToken: String,
        jobReference: String,
        statusUrl: URI,
    ): ResponseInputStream? = ResponseInputStream(InputStream.nullInputStream(), 1L)
}
