package no.nav.sosialhjelp.avtaler.digipost

import no.digipost.api.client.DigipostClient
import no.digipost.api.client.DigipostClientConfig
import no.digipost.api.client.SenderId
import no.digipost.api.client.security.Signer
import no.nav.sosialhjelp.avtaler.avtaler.Avtale
import java.nio.file.Files
import java.nio.file.Paths

class DigipostApiClient {
    fun sendTilSignering(fnr: String, avtale: Avtale) {
        val senderId: SenderId = SenderId.of(123456)

        var signer: Signer
        Files.newInputStream(Paths.get("certificate.p12")).use { sertifikatInputStream ->
            signer = Signer.usingKeyFromPKCS12KeyStore(sertifikatInputStream, "TheSecretPassword")
        }

        val client = DigipostClient(
            DigipostClientConfig.newConfiguration().build(), senderId.asBrokerId(), signer
        )
    }
}
