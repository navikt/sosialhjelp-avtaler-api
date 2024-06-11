package no.nav.sosialhjelp.avtaler

import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType

object Configuration {
    private val defaultProperties =
        ConfigurationMap(
            mapOf(
                "application.baseUrl" to "http://localhost:5000/sosialhjelp/avtaler",
                "userclaim" to "pid",
                "TOKEN_X_CLIENT_ID" to "abc",
                "TOKEN_X_WELL_KNOWN_URL" to "abc",
                "TOKEN_X_TOKEN_ENDPOINT" to "",
                "TOKEN_X_PRIVATE_JWK" to "",
                "AZURE_APP_CLIENT_ID" to "",
                "AZURE_APP_WELL_KNOWN_URL" to "",
                "AZURE_OPENID_CONFIG_JWKS_URI" to "",
                "AZURE_OPENID_CONFIG_ISSUER" to "",
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
                "pdl.audience" to "",
                "ereg.url" to "",
                "gotenberg.url" to "",
            ),
        )

    private val localProperties =
        ConfigurationMap(
            mapOf(
                "application.profile" to "LOCAL",
                "application.cluster" to "LOCAL",
                "TOKEN_X_WELL_KNOWN_URL" to "http://host.docker.internal:8080/default/.well-known/openid-configuration",
                "TOKEN_X_CLIENT_ID" to "local",
                "userclaim" to "sub",
                "AZURE_APP_CLIENT_ID" to "local",
                "AZURE_APP_WELL_KNOWN_URL" to "http://host.docker.internal:8080/default/.well-known/openid-configuration",
                "AZURE_OPENID_CONFIG_JWKS_URI" to "",
                "AZURE_OPENID_CONFIG_ISSUER" to "",
                "POSTGRES_DATABASE" to "",
                "POSTGRES_USERNAME" to "",
                "POSTGRES_PASSWORD" to "",
                "POSTGRES_HOST" to "",
                "POSTGRES_PORT" to "",
                "gcp.bucketName" to "digisos-avtaler",
                "ereg.url" to "https://ereg-services.dev-fss-pub.nais.io",
                "gotenberg.url" to "http://gotenberg:3000",
            ),
        )

    private val devProperties =
        ConfigurationMap(
            mapOf(
                "application.baseUrl" to "https://digisos.ansatt.dev.nav.no/sosialhjelp/avtaler",
                "application.profile" to "DEV",
                "application.cluster" to "DEV-GCP",
                "altinn.altinnUrl" to "https://altinn-rettigheter-proxy.intern.dev.nav.no/altinn-rettigheter-proxy",
                "altinn.proxyConsumerId" to "sosialhjelp-avtaler-api-dev",
                "enhetsregisteret_base_url" to "http://sosialhjelp-mock-alt-api-mock/sosialhjelp/mock-alt-api/enhetsregisteret/api",
                "altinn.altinnRettigheterAudience" to "dev-gcp:arbeidsgiver:altinn-rettigheter-proxy",
                "virksomhetssertifikat.projectId" to "virksomhetssertifikat-dev",
                "virksomhetssertifikat.secretId" to "test-virksomhetssertifikat-felles-keystore-jceks_2018-2021",
                "virksomhetssertifikat.versionId" to "3",
                "virksomhetssertifikat.passwordProjectId" to "virksomhetssertifikat-dev",
                "virksomhetssertifikat.passwordSecretId" to "test-keystore-credentials-json",
                "virksomhetssertifikat.passwordSecretVersion" to "2",
                "pdl.url" to "https://pdl-api.dev-fss-pub.nais.io/graphql",
                "pdl.audience" to "dev-fss:pdl:pdl-api",
                "gcp.bucketName" to "digisos-nks-avtaler-dev",
                "ereg.url" to "https://ereg-services.dev-fss-pub.nais.io",
                "gotenberg.url" to "http://sosialhjelp-konvertering-til-pdf",
            ),
        )

    private val prodProperties =
        ConfigurationMap(
            mapOf(
                "application.baseUrl" to "https://nav.no/sosialhjelp/avtaler",
                "application.profile" to "PROD",
                "application.cluster" to "PROD-GCP",
                "altinn.altinnUrl" to
                    "http://altinn-rettigheter-proxy.arbeidsgiver.svc.cluster.local/altinn-rettigheter-proxy",
                "altinn.proxyConsumerId" to "sosialhjelp-avtaler-api",
                "altinn.altinnRettigheterAudience" to "prod-gcp:arbeidsgiver:altinn-rettigheter-proxy",
                "virksomhetssertifikat.projectId" to "virksomhetssertifikat-prod",
                "virksomhetssertifikat.secretId" to "virksomhetssertifikat-digisos-keystore-jceks_2021-2024",
                "virksomhetssertifikat.versionId" to "4",
                "virksomhetssertifikat.passwordProjectId" to "virksomhetssertifikat-prod",
                "virksomhetssertifikat.passwordSecretId" to "digisos-keystore-credentials-json",
                "virksomhetssertifikat.passwordSecretVersion" to "4",
                "pdl.url" to "https://pdl-api.prod-fss-pub.nais.io/graphql",
                "pdl.audience" to "prod-fss:pdl:pdl-api",
                "gcp.bucketName" to "digisos-nks-avtaler",
                "ereg.url" to "https://ereg-services.prod-fss-pub.nais.io",
                "gotenberg.url" to "https://konvertering-til-pdf.prod-fss-pub.nais.io",
            ),
        )

    private val resourceProperties =
        when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
            "dev-gcp" -> devProperties
            "prod-gcp" -> prodProperties
            else -> localProperties
        }

    private val config = systemProperties() overriding EnvironmentVariables() overriding resourceProperties overriding defaultProperties

    val profile: Profile = this["application.profile"].let { Profile.valueOf(it) }
    val local: Boolean = profile == Profile.LOCAL
    val dev: Boolean = profile == Profile.DEV
    val prod: Boolean = profile == Profile.PROD

    val tokenXProperties = TokenXProperties()
    val altinnProperties = AltinnProperties()
    val pdlProperties = PdlProperties()
    val dbProperties = DatabaseProperties()
    val digipostProperties = DigipostProperties()
    val virksomhetssertifikatProperties = VirksomhetssertifikatProperties()
    val eregProperties = EregProperties()
    val slackProperties = SlackProperties()
    val gcpProperties = GcpProperties()
    val azureProperties = AzureProperties()
    val gotenbergProperties = GotenbergProperties()

    operator fun get(key: String): String = config[Key(key, stringType)]

    data class TokenXProperties(
        val clientId: String = this["TOKEN_X_CLIENT_ID"],
        val wellKnownUrl: String = this["TOKEN_X_WELL_KNOWN_URL"],
        val userclaim: String = this["userclaim"],
        val privateJwk: String = this["TOKEN_X_PRIVATE_JWK"],
        val tokenXTokenEndpoint: String = this["TOKEN_X_TOKEN_ENDPOINT"],
    )

    data class AzureProperties(
        val clientId: String = this["AZURE_APP_CLIENT_ID"],
        val wellKnownUrl: String = this["AZURE_APP_WELL_KNOWN_URL"],
        val userclaim: String = this["userclaim"],
        val privateJwk: String = this["AZURE_OPENID_CONFIG_JWKS_URI"],
        val issuer: String = this["AZURE_OPENID_CONFIG_ISSUER"],
    )

    enum class Profile {
        LOCAL,
        DEV,
        PROD,
    }

    enum class Cluster {
        PROD_GCP,
        DEV_GCP,
        LOCAL,
        ;

        companion object {
            fun fromNaisString(cluster: String): Cluster {
                return when (cluster) {
                    "PROD-GCP" -> PROD_GCP
                    "DEV-GCP" -> PROD_GCP
                    "LOCAL" -> PROD_GCP
                    else -> error("Ukjent cluster-navn")
                }
            }
        }
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
        val pdlAudience: String = this["pdl.audience"],
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
        val navOrgnr: String = this["digipost.navOrgnr"],
    )

    data class VirksomhetssertifikatProperties(
        val projectId: String = this["virksomhetssertifikat.projectId"],
        val secretId: String = this["virksomhetssertifikat.secretId"],
        val versionId: String = this["virksomhetssertifikat.versionId"],
        val passwordProjectId: String = this["virksomhetssertifikat.passwordProjectId"],
        val passwordSecretId: String = this["virksomhetssertifikat.passwordSecretId"],
        val passwordSecretVersionId: String = this["virksomhetssertifikat.passwordSecretVersion"],
    )

    data class EregProperties(val baseUrl: String = this["ereg.url"])

    data class SlackProperties(
        // The Slack-webhook is extracted from the environment variable SLACK_HOOK (envFrom: digisos-slack-hook)
        val slackHook: String = this["SLACK_HOOK"],
        val environment: String = profile.toString(),
    )

    data class GcpProperties(val bucketName: String = this["gcp.bucketName"])

    data class GotenbergProperties(val url: String = this["gotenberg.url"])
}
