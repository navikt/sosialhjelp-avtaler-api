package no.nav.sosialhjelp.avtaler.gcpbucket

import com.google.api.gax.retrying.RetrySettings
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.StorageOptions
import com.google.common.net.MediaType
import org.threeten.bp.Duration

class GcpBucket(private val bucketName: String) {
    private val retrySetting = RetrySettings.newBuilder().setTotalTimeout(Duration.ofMillis(3000)).build()
    private val storage = StorageOptions.newBuilder().setRetrySettings(retrySetting).build().service

    fun lagreBlob(
        blobName: String,
        contentType: MediaType,
        metadata: Map<String, String>,
        bytes: ByteArray,
    ) {
        val contentTypeVerdi = contentType.toString()
        val blobInfo =
            BlobInfo.newBuilder(bucketName, blobName)
                .setContentType(contentTypeVerdi)
                .setMetadata(metadata + mapOf("content-type" to contentTypeVerdi))
                .build()

        storage.create(blobInfo, bytes)
    }

    fun finnesFil(blobNavn: String): Boolean {
        val blob = storage.get(bucketName, blobNavn)
        return blob != null && blob.size > 0
    }
}
