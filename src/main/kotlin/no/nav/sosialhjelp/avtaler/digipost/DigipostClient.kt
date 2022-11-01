package no.nav.sosialhjelp.avtaler.digipost

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
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Arrays
import java.util.Collections

class DigipostClient(props: Configuration.DigipostProperties) {
    val exitUrls = ExitUrls.of(
        URI.create(props.onCompletionUrl),
        URI.create("http://sender.org/onRejection"),
        URI.create("http://sender.org/onError")
    )
    private val keyStoreConfig: KeyStoreConfig = configure()
    private val clientConfiguration = ClientConfiguration.builder(keyStoreConfig)
        .trustStore(Certificates.TEST)
        .serviceUri(ServiceUri.DIFI_TEST)
        .globalSender(Sender("123456789"))
        .build()
    private val certificatePath = props.certificatePath
    private val certificatePassword = props.certificatePassword
    private val keyStorePassword = props.keyStorePassword

    private fun configure(): KeyStoreConfig {
        var keyStoreConfig: KeyStoreConfig
        Files.newInputStream(Paths.get(certificatePath)).use { certificateStream ->
            keyStoreConfig = KeyStoreConfig.fromJavaKeyStore(
                certificateStream,
                "OrganizationCertificateAlias",
                keyStorePassword,
                certificatePassword
            )
        }
        return keyStoreConfig
    }

    fun sendTilSignering(fnr: String) {
        val client = DirectClient(clientConfiguration)

        val avtalePdf: ByteArray? = getAvtalePdf() // Load document bytes

        val documents: List<DirectDocument> = Arrays.asList(
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

        val jobResponse = client.create(job)
    }

    private fun getAvtalePdf(): ByteArray? {
        TODO("Not yet implemented")
    }
}
