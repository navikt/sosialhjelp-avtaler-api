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
            "REDIS_HOST" to "localhost",
            "REDIS_PASSWORD" to "",
        )
    )

    private val devProperties = ConfigurationMap(
        mapOf(
            "application.profile" to "DEV",
            "application.cluster" to "DEV-GCP",
            "altinn.altinnUrl" to "https://altinn-rettigheter-proxy.dev.nav.no/altinn-rettigheter-proxy/ekstern/altinn",
            "altinn.proxyConsumerId" to "sosialhjelp-avtaler-api-dev",
            "altinn.altinnRettigheterAudience" to "dev-gcp:arbeidsgiver:altinn-rettigheter-proxy",
            "pdl.url" to "https://pdl-api.dev-fss-pub.nais.io/graphql",
            "pdl.audience" to "dev-fss:pdl:pdl-api"
        )
    )

    private val prodProperties = ConfigurationMap(
        mapOf()
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
    val dbProperties = DatabaseProperties()

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
        val baseUrl: String = this["pdl.url"],
        val pdlAudience: String = this["pdl.audience"]
    )

    data class DatabaseProperties(
        val databaseNavn: String = this["POSTGRES_DATABASE"],
        val databaseUser: String = this["POSTGRES_USERNAME"],
        val databasePassword: String = this["POSTGRES_PASSWORD"],
        val databaseHost: String = this["POSTGRES_HOST"],
        val databasePort: String = this["POSTGRES_PORT"],
    )
}
