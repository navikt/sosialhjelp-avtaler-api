package no.nav.sosialhjelp.avtaler.secretmanager

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient
import com.google.cloud.secretmanager.v1.SecretPayload
import com.google.cloud.secretmanager.v1.SecretVersionName
import mu.KotlinLogging
import java.io.IOException
import java.util.zip.CRC32C
import java.util.zip.Checksum

private val log = KotlinLogging.logger {}

object SecretManager {
    @Throws(IOException::class)
    fun accessSecretVersion(
        passwordProjectId: String?,
        passwordSecretId: String?,
        passwordVersionId: String?,
    ): SecretPayload? {
        // Initialize client that will be used to send requests. This client only needs to be created
        // once, and can be reused for multiple requests. After completing all of your requests, call
        // the "close" method on the client to safely clean up any remaining background resources.
        SecretManagerServiceClient.create().use { client ->
            val secretVersionName =
                SecretVersionName.of(passwordProjectId, passwordSecretId, passwordVersionId)

            val response = client.accessSecretVersion(secretVersionName)

            val data = response.payload.data.toByteArray()
            val checksum: Checksum = CRC32C()
            checksum.update(data, 0, data.size)
            if (response.payload.dataCrc32C != checksum.value) {
                log.error("Data corruption detected.")
                throw IOException("Data corruption detected.")
            }

            return response.payload
        }
    }
}
