package no.nav.sosialhjelp.avtaler.digipost

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import mu.KotlinLogging
import no.digipost.signature.client.ClientConfiguration
import no.digipost.signature.client.ServiceEnvironment
import no.digipost.signature.client.core.DocumentType
import no.digipost.signature.client.core.ResponseInputStream
import no.digipost.signature.client.core.Sender
import no.digipost.signature.client.direct.DirectClient
import no.digipost.signature.client.direct.DirectDocument
import no.digipost.signature.client.direct.DirectJob
import no.digipost.signature.client.direct.DirectJobResponse
import no.digipost.signature.client.direct.DirectJobStatus
import no.digipost.signature.client.direct.DirectSigner
import no.digipost.signature.client.direct.ExitUrls
import no.digipost.signature.client.direct.StatusReference
import no.digipost.signature.client.security.KeyStoreConfig
import no.nav.sosialhjelp.avtaler.Configuration
import no.nav.sosialhjelp.avtaler.avtaler.Avtale
import no.nav.sosialhjelp.avtaler.exceptions.VirsomhetsertifikatException
import no.nav.sosialhjelp.avtaler.secretmanager.AccessSecretVersion
import no.nav.sosialhjelp.avtaler.secretmanager.DigisosKeyStoreCredentials
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.Collections
import java.util.UUID

private val log = KotlinLogging.logger {}

data class DigipostResponse(val redirectUrl: URI, val signerUrl: URI, val reference: String)
class DigipostClient(props: Configuration.DigipostProperties, virksomhetProps: Configuration.VirksomhetssertifikatProperties, profile: Configuration.Profile) {
    private val accessSecretVersion: AccessSecretVersion = AccessSecretVersion
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
    private val clientConfiguration = ClientConfiguration.builder(keyStoreConfig)
        .serviceEnvironment(if (profile == Configuration.Profile.PROD) ServiceEnvironment.PRODUCTION else ServiceEnvironment.DIFITEST)
        .defaultSender(Sender(props.navOrgnr))
        .build()
    private val client = DirectClient(clientConfiguration)

    private fun configure(accessSecretVersion: AccessSecretVersion): KeyStoreConfig {
        val certificatePassword = accessSecretVersion.accessSecretVersion(virksomhetPasswordProjectId, virksomhetPasswordSecretId, virksomhetPasswordVersionId)?.data?.toStringUtf8()
        val objectMapper = ObjectMapper().registerKotlinModule()
        val keystoreCredentials: DigisosKeyStoreCredentials = objectMapper.readValue(certificatePassword, DigisosKeyStoreCredentials::class.java)

        val secretPayload = accessSecretVersion.accessSecretVersion(virksomhetProjectId, virksomhetSecretId, virksomhetVersionId)

        val inputStream = try {
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
            keystoreCredentials.password
        )
    }

    fun sendTilSignering(fnr: String, avtale: Avtale): DigipostResponse {
        val exitUrls = ExitUrls.of(
            URI.create(onCompletionUrl + avtale.orgnr),
            URI.create(onRejectionUrl + avtale.orgnr),
            URI.create(onErrorUrl + avtale.orgnr)
        )

        val avtalePdf: ByteArray
        try {
            avtalePdf = getAvtalePdf()
        } catch (e: NullPointerException) {
            log.error("Kunne ikke laste inn avtale.pdf")
            throw e
        }

        val avtaleTittel = "Avtale om påkobling til innsynsflate NKS"
        val documents: List<DirectDocument> = listOf(
            DirectDocument.builder(avtaleTittel, avtalePdf).type(DocumentType.PDF).build()
        )

        val signers: List<DirectSigner> = Collections.singletonList(
            DirectSigner
                .withPersonalIdentificationNumber(fnr)
                .build()
        )

        val job = DirectJob
            .builder(avtaleTittel, documents, signers, exitUrls)
            .withReference(UUID.randomUUID().toString())
            .build()

        val directJobResponse = client.create(job)
        if (directJobResponse.singleSigner.redirectUrl == null) {
            log.error("Kan ikke redirecte bruker til e-signering. Redirect URL fra Digipost er null.")
            throw DigipostException("Redirect URL fra Digipost er null.")
        }
        return DigipostResponse(directJobResponse.singleSigner.redirectUrl, directJobResponse.statusUrl, directJobResponse.reference)
    }

    fun sjekkSigneringsstatus(directJobReference: String, statusUrl: URI, statusQueryToken: String): DirectJobStatus {
        val directJobResponse = DirectJobResponse(1, directJobReference, statusUrl, null)
        val directJobStatusResponse = client.getStatus(
            StatusReference.of(directJobResponse)
                .withStatusQueryToken(statusQueryToken)
        )
        return directJobStatusResponse.status
    }

    fun hentSignertAvtale(statusQueryToken: String, jobReference: String, statusUrl: URI): ResponseInputStream? {
        val directJobResponse = DirectJobResponse(1, jobReference, statusUrl, null)
        val directJobStatusResponse = client.getStatus(StatusReference.of(directJobResponse).withStatusQueryToken(statusQueryToken))

        return if (directJobStatusResponse.isPAdESAvailable) client.getPAdES(directJobStatusResponse.getpAdESUrl()) else null
    }

    private fun getAvtalePdf(): ByteArray {
        return this::class.java.getResource("/avtaler/Avtale.pdf")!!.openStream().readAllBytes()
    }
}
