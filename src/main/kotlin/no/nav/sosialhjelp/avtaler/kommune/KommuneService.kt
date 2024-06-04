package no.nav.sosialhjelp.avtaler.kommune

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.CacheControl
import io.ktor.http.headers
import io.ktor.server.response.cacheControl
import java.time.LocalDate

const val URL = "https://data.ssb.no/api/klass/v1/classifications/582"

data class ClassificationResponse(val versions: List<ClassificationVersion>)

data class ClassificationVersion(val validFrom: LocalDate, val validTo: LocalDate? = null, val name: String, val _links: Link)

data class Link(val self: Self)

data class Self(val href: String)

class KommuneService(private val httpClient: HttpClient) {
    suspend fun getAlleKommuner(): List<Kommune> {
        val classResponse =
            httpClient.get(URL) {
                headers {
                    header("Accept", "application/json")

                    // Caches i HttpClient i inntil én uke
                    cacheControl(CacheControl.MaxAge(60 * 60 * 24 * 7))
                }
            }.body<ClassificationResponse>()

        val currentVersion =
            classResponse.versions.find {
                it.validTo != null && it.validTo >= LocalDate.now() && it.validFrom <= LocalDate.now()
            } ?: classResponse.versions.find {
                it.validFrom <= LocalDate.now() && it.validTo == null
            } ?: error("Fant ikke gyldig versjon av kommuneklassifisering")
        val versionUrl = currentVersion._links.self.href
        val classicationVersionResponse =
            httpClient.get(versionUrl) {
                headers {
                    header("Accept", "application/json")

                    // Caches i HttpClient i inntil én uke
                    cacheControl(CacheControl.MaxAge(60 * 60 * 24 * 7))
                }
            }.body<ClassicationVersionResponse>()
        return classicationVersionResponse.classificationItems.map { Kommune(it.code, it.name) }
    }
}

data class ClassicationVersionResponse(val classificationItems: List<ClassificationItem>)

data class ClassificationItem(val code: String, val name: String)

data class Kommune(val orgnr: String, val navn: String)
