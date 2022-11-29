package no.nav.sosialhjelp.avtaler

import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType

object Configuration {

    private val defaultProperties = ConfigurationMap(
        mapOf(
            "application.baseUrl" to "http://localhost:5000/sosialhjelp/avtaler",
            "userclaim" to "pid",
            "TOKEN_X_CLIENT_ID" to "abc",
            "TOKEN_X_WELL_KNOWN_URL" to "abc",
            "TOKEN_X_TOKEN_ENDPOINT" to "",
            "TOKEN_X_PRIVATE_JWK" to "",
            "unleash.unleash-uri" to "https://unleash.nais.io/api/",
            "altinn.altinnUrl" to "",
            "altinn.proxyConsumerId" to "",
            "altinn.altinnRettigheterAudience" to "",
            "ALTINN_APIKEY" to "dummyverdi",
            "ALTINN_APIGW_APIKEY" to "dummyverdi",
            "digipost.keyStorePassword" to "KeyStorePassword",
            "digipost.certificatePassword" to "CertificatePassword",
            "digipost.onCompletionUrl" to "/opprett-avtale/suksess/",
            "digipost.onErrorUrl" to "/opprett-avtale/feil/",
            "digipost.onRejectionUrl" to "/opprett-avtale/feil/",
            "digipost.navOrgnr" to "889640782",
            "enhetsregisteret_base_url" to "https://data.brreg.no/enhetsregisteret/api",
            "virksomhetssertifikat.projectId" to "virksomhetssertifikat-dev",
            "virksomhetssertifikat.secretId" to "test-virksomhetssertifikat-felles-keystore-jceks_2018-2021",
            "virksomhetssertifikat.versionId" to "3",
            "virksomhetssertifikat.passwordProjectId" to "virksomhetssertifikat-dev",
            "virksomhetssertifikat.passwordSecretId" to "test-keystore-credentials-json",
            "virksomhetssertifikat.passwordSecretVersion" to "2",
            "pdl.url" to "",
            "pdl.audience" to ""
        )
    )

    private val localProperties = ConfigurationMap(
        mapOf(
            "application.profile" to "LOCAL",
            "application.cluster" to "LOCAL",
            "TOKEN_X_WELL_KNOWN_URL" to "http://host.docker.internal:8080/default/.well-known/openid-configuration",
            "TOKEN_X_CLIENT_ID" to "local",
            "userclaim" to "sub",
            "POSTGRES_DATABASE" to "",
            "POSTGRES_USERNAME" to "",
            "POSTGRES_PASSWORD" to "",
            "POSTGRES_HOST" to "",
            "POSTGRES_PORT" to ""
        )
    )

    private val devProperties = ConfigurationMap(
        mapOf(

            "application.baseUrl" to "https://digisos.dev.nav.no/sosialhjelp/avtaler",
            "application.profile" to "DEV",
            "application.cluster" to "DEV-GCP",
            "altinn.altinnUrl" to "https://altinn-rettigheter-proxy.dev.nav.no/altinn-rettigheter-proxy/ekstern/altinn",
            "altinn.proxyConsumerId" to "sosialhjelp-avtaler-api-dev",
            "enhetsregisteret_base_url" to "https://data.brreg.no/enhetsregisteret/api",
            "altinn.altinnRettigheterAudience" to "dev-gcp:arbeidsgiver:altinn-rettigheter-proxy",
            "virksomhetssertifikat.projectId" to "virksomhetssertifikat-dev",
            "virksomhetssertifikat.secretId" to "test-virksomhetssertifikat-felles-keystore-jceks_2018-2021",
            "virksomhetssertifikat.versionId" to "3",
            "virksomhetssertifikat.passwordProjectId" to "virksomhetssertifikat-dev",
            "virksomhetssertifikat.passwordSecretId" to "test-keystore-credentials-json",
            "virksomhetssertifikat.passwordSecretVersion" to "2",
            "pdl.url" to "https://pdl-api.dev-fss-pub.nais.io/graphql",
            "pdl.audience" to "dev-fss:pdl:pdl-api"
        )
    )

    private val prodProperties = ConfigurationMap(
        mapOf(
            "application.baseUrl" to "https://nav.no/sosialhjelp/avtaler",
            "application.profile" to "PROD",
            "application.cluster" to "PROD-GCP",
            "altinn.altinnUrl" to "http://altinn-rettigheter-proxy.arbeidsgiver.svc.cluster.local", // dobbeltsjekk denne
            "altinn.proxyConsumerId" to "sosialhjelp-avtaler-api",
            "altinn.altinnRettigheterAudience" to "prod-gcp:arbeidsgiver:altinn-rettigheter-proxy",
            "virksomhetssertifikat.projectId" to "virksomhetssertifikat-prod",
            "virksomhetssertifikat.secretId" to "virksomhetssertifikat-digisos-keystore-jceks_2021-2024",
            "virksomhetssertifikat.versionId" to "1",
            "virksomhetssertifikat.passwordProjectId" to "virksomhetssertifikat-prod",
            "virksomhetssertifikat.passwordSecretId" to "digisos-keystore-credentials-json",
            "virksomhetssertifikat.passwordSecretVersion" to "1",
            "pdl.url" to "https://pdl-api.prod-fss-pub.nais.io/graphql",
            "pdl.audience" to "prod-fss:pdl:pdl-api"
        )
    )

    private val resourceProperties =
        when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
            "dev-gcp" -> devProperties
            "prod-gcp" -> prodProperties
            else -> localProperties
        }

    private val config = systemProperties() overriding EnvironmentVariables() overriding resourceProperties overriding defaultProperties

    val profile: Profile = this["application.profile"].let { Profile.valueOf(it) }
    val cluster: Cluster = this["application.cluster"].let { Cluster.valueOf(it) }
    val local: Boolean = profile == Profile.LOCAL
    val dev: Boolean = profile == Profile.DEV
    val prod: Boolean = profile == Profile.PROD

    val tokenXProperties = TokenXProperties()
    val altinnProperties = AltinnProperties()
    val pdlProperties = PdlProperties()
    val dbProperties = DatabaseProperties()
    val digipostProperties = DigipostProperties()
    val virksomhetssertifikatProperties = VirksomhetssertifikatProperties()
    val enhetsregistertetProperties = EnhetsregistertetProperties()

    operator fun get(key: String): String = config[Key(key, stringType)]

    data class TokenXProperties(
        val clientId: String = this["TOKEN_X_CLIENT_ID"],
        val wellKnownUrl: String = this["TOKEN_X_WELL_KNOWN_URL"],
        val userclaim: String = this["userclaim"],
        val privateJwk: String = this["TOKEN_X_PRIVATE_JWK"],
        val tokenXTokenEndpoint: String = this["TOKEN_X_TOKEN_ENDPOINT"],
    )

    enum class Profile {
        LOCAL, DEV, PROD
    }

    enum class Cluster {
        `PROD-GCP`, `DEV-GCP`, `LOCAL`
    }

    data class AltinnProperties(
        val baseUrl: String = this["altinn.altinnUrl"],
        val proxyConsumerId: String = this["altinn.proxyConsumerId"],
        val apiKey: String = this["ALTINN_APIKEY"],
        val apiGWKey: String = this["ALTINN_APIGW_APIKEY"],
        val altinnRettigheterAudience: String = this["altinn.altinnRettigheterAudience"],
    )

    data class PdlProperties(
        val pdlUrl: String = this["pdl.url"],
        val pdlAudience: String = this["pdl.audience"]
    )

    data class DatabaseProperties(
        val databaseNavn: String = this["POSTGRES_DATABASE"],
        val databaseUser: String = this["POSTGRES_USERNAME"],
        val databasePassword: String = this["POSTGRES_PASSWORD"],
        val databaseHost: String = this["POSTGRES_HOST"],
        val databasePort: String = this["POSTGRES_PORT"],
    )

    data class DigipostProperties(
        val onCompletionUrl: String = this["application.baseUrl"] + this["digipost.onCompletionUrl"],
        val onRejectionUrl: String = this["application.baseUrl"] + this["digipost.onRejectionUrl"],
        val onErrorUrl: String = this["application.baseUrl"] + this["digipost.onErrorUrl"],
        val navOrgnr: String = this["digipost.navOrgnr"]
    )

    data class VirksomhetssertifikatProperties(
        val projectId: String = this["virksomhetssertifikat.projectId"],
        val secretId: String = this["virksomhetssertifikat.secretId"],
        val versionId: String = this["virksomhetssertifikat.versionId"],
        val passwordProjectId: String = this["virksomhetssertifikat.passwordProjectId"],
        val passwordSecretId: String = this["virksomhetssertifikat.passwordSecretId"],
        val passwordSecretVersionId: String = this["virksomhetssertifikat.passwordSecretVersion"]
    )

    data class EnhetsregistertetProperties(
        val baseUrl: String = this["enhetsregisteret_base_url"]
    )
}
