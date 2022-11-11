package no.nav.sosialhjelp.avtaler.digipost

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import mu.KotlinLogging
import no.digipost.signature.client.Certificates
import no.digipost.signature.client.ClientConfiguration
import no.digipost.signature.client.ServiceUri
import no.digipost.signature.client.core.Sender
import no.digipost.signature.client.direct.DirectClient
import no.digipost.signature.client.direct.DirectDocument
import no.digipost.signature.client.direct.DirectJob
import no.digipost.signature.client.direct.DirectSigner
import no.digipost.signature.client.direct.ExitUrls
import no.digipost.signature.client.security.KeyStoreConfig
import no.nav.sosialhjelp.avtaler.Configuration
import no.nav.sosialhjelp.avtaler.avtaler.Avtale
import no.nav.sosialhjelp.avtaler.exceptions.VirsomhetsertifikatException
import no.nav.sosialhjelp.avtaler.secretmanager.AccessSecretVersion
import no.nav.sosialhjelp.avtaler.secretmanager.DigisosKeyStoreCredentials
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.Collections

private val log = KotlinLogging.logger {}

class DigipostClient(props: Configuration.DigipostProperties, virksomhetProps: Configuration.VirksomhetssertifikatProperties) {
    private val accessSecretVersion: AccessSecretVersion = AccessSecretVersion
    private val avtalePdfPath = props.avtalePdfPath
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
        .trustStore(Certificates.TEST)
        .serviceUri(ServiceUri.DIFI_TEST)
        .globalSender(Sender(props.navOrgnr))
        .build()

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

        log.info("lengde sertifikat: {}", secretPayload.data.size())

        return KeyStoreConfig.fromJavaKeyStore(
            inputStream,
            keystoreCredentials.alias,
            keystoreCredentials.password,
            keystoreCredentials.password
        )
    }

    fun sendTilSignering(fnr: String, avtale: Avtale): URI {
        val exitUrls = ExitUrls.of(
            URI.create(onCompletionUrl + avtale.orgnr),
            URI.create(onRejectionUrl + avtale.orgnr),
            URI.create(onErrorUrl + avtale.orgnr)
        )

        val client = DirectClient(clientConfiguration)

        val avtalePdf: ByteArray? = getAvtalePdf()

        val documents: List<DirectDocument> = listOf(
            DirectDocument.builder("Digisos avtale 1 title", avtalePdf).build()
        )

        val signers: List<DirectSigner> = Collections.singletonList(
            DirectSigner
                .withPersonalIdentificationNumber(fnr)
                .build()
        )

        val job = DirectJob
            .builder("Digisos: avtalesignering", documents, signers, exitUrls)
            .build()

        val directJobResponse = client.create(job)
        if (directJobResponse.singleSigner.signerUrl == null) {
            log.error("Signer URL fra digipost er null.")
            throw DigipostException("Signer URL fra Digipost er null.")
        }
        return directJobResponse.singleSigner.signerUrl
    }

    private fun getAvtalePdf(): ByteArray? {
        return this::class.java.getResourceAsStream(avtalePdfPath)?.readAllBytes()
    }
}
