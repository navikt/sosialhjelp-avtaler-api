package no.nav.sosialhjelp.avtaler.gotenberg

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import mu.KotlinLogging

private const val LIBRE_OFFICE_ROUTE = "/forms/libreoffice/convert"

private val log = KotlinLogging.logger { }

class GotenbergClient(
    private val httpClient: HttpClient,
    private val gotenbergUrl: String,
) {
    suspend fun convertToPdf(
        filename: String,
        bytes: ByteArray,
    ): ByteArray? {
        log.info { "Konverterer $filename til pdf på url $gotenbergUrl" }
        val response =
            httpClient.submitFormWithBinaryData(
                "$gotenbergUrl$LIBRE_OFFICE_ROUTE",
                formData {
                    append(
                        "files",
                        bytes,
                        headers =
                            Headers.build {
                                append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                                append(HttpHeaders.ContentType, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                            },
                    )
                },
            )

        val traceHeader = response.headers["gotenberg-trace"] ?: "[N/A]"
        if (!response.status.isSuccess()) {
            log.error {
                "Gotenberg conversion failed with status ${response.status.value} and trace $traceHeader."
            }
            val bodyAsText = response.bodyAsText()
            log.error { "Gotenberg response body: $bodyAsText" }
            return null
        }

        return response.body()
    }
}
