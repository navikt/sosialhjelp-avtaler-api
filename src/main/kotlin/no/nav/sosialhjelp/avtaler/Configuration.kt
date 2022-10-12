package no.nav.sosialhjelp.avtaler

import com.fasterxml.jackson.annotation.JsonProperty
import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import com.nimbusds.jose.Algorithm
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import no.nav.sosialhjelp.avtaler.HttpClientConfig.httpClient
import java.util.UUID

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
            "MASKINPORTEN_CLIENT_ID" to "",
            "MASKINPORTEN_ISSUER" to "",
            "MASKINPORTEN_SCOPES" to "",
            "MASKINPORTEN_TOKEN_ENDPOINT" to "",
            "MASKINPORTEN_CLIENT_JWK" to "",
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
            "MASKINPORTEN_SCOPES" to "scope1 scope2",
            "MASKINPORTEN_CLIENT_ID" to UUID.randomUUID().toString(),
            "MASKINPORTEN_WELL_KNOWN_URL" to "https://ver2.maskinporten.no/.well-known/oauth-authorization-server",
        )
    )

    private val devProperties = ConfigurationMap(
        mapOf(
            "application.profile" to "DEV",
            "application.cluster" to "DEV-GCP",
            "altinn.altinnUrl" to "https://tt02.altinn.no",
            "altinn.proxyConsumerId" to "sosialhjelavtaler-api-dev",
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

    fun getOrNull(key: String): String? = config.getOrNull(Key(key, stringType))

    val profile: Profile = this["application.profile"].let { Profile.valueOf(it) }
    val cluster: Cluster = this["application.cluster"].let { Cluster.valueOf(it) }
    val local: Boolean = profile == Profile.LOCAL
    val dev: Boolean = profile == Profile.DEV
    val prod: Boolean = profile == Profile.PROD

    val tokenXProperties = TokenXProperties()
    val altinnProperties = AltinnProperties()
    val maskinportenProperties: MaskinportenProperties = MaskinportenProperties()

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
    )

    private fun getRSAkey(clientJwk: String?): RSAKey {
        if (clientJwk.isNullOrEmpty()) {
            return generateRSAKey()
        }
        return clientJwk.let {
            RSAKey.parse(it)
        }
    }

    private fun generateRSAKey() = RSAKeyGenerator(2048)
        .keyID(UUID.randomUUID().toString())
        .keyUse(KeyUse.SIGNATURE)
        .algorithm(Algorithm.parse("RS256"))
        .generate()

    data class MaskinportenProperties(
        val clientId: String = this["MASKINPORTEN_CLIENT_ID"],
        val issuer: String = this["MASKINPORTEN_ISSUER"],
        val scopes: String = this["MASKINPORTEN_SCOPES"],
        val tokenEndpointUrl: String = this["MASKINPORTEN_TOKEN_ENDPOINT"],
        val privateJwk: String = this["MASKINPORTEN_CLIENT_JWK"],
        val altinnUrl: String = this["altinn.altinnUrl"],
        val wellKnownUrl: String = this["MASKINPORTEN_WELL_KNOWN_URL"],
        val clientJwk: RSAKey = getRSAkey(getOrNull("MASKINPORTEN_CLIENT_JWK")),
    )

    data class Metadata(
        @JsonProperty("issuer") val issuer: String,
        @JsonProperty("jwks_uri") val jwksUri: String,
        @JsonProperty("token_endpoint") val tokenEndpoint: String
    )

    fun wellKnowConfig(wellKnownUrl: String): Metadata {
        val metadata: Metadata by lazy {
            runBlocking { httpClient().get(wellKnownUrl).body() }
        }
        return metadata
    }
}
