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
            "unleash.unleash-uri" to "https://unleash.nais.io/api/",
            "altinn.altinnUrl" to "",
            "altinn.proxyConsumerId" to "",
            "ALTINN_APIKEY" to "",
            "ALTINN_APIGW_APIKEY" to "",
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
            "altinn.altinnUrl" to "https://altinn-rettigheter-proxy.intern.nav.no/altinn-rettigheter-proxy/ekstern/altinn/api/serviceowner/",
            "altinn.proxyConsumerId" to "sosialhjelp-avtaler-api",
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

    operator fun get(key: String): String = config[Key(key, stringType)]

    data class TokenXProperties(
        val clientId: String = this["TOKEN_X_CLIENT_ID"],
        val wellKnownUrl: String = this["TOKEN_X_WELL_KNOWN_URL"],
        val userclaim: String = this["userclaim"],
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
    )
}
