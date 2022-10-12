package no.nav.sosialhjelp.avtaler.maskinporten


import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.fasterxml.jackson.annotation.JsonAlias
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.parametersOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import no.nav.sosialhjelp.avtaler.Configuration
import no.nav.sosialhjelp.avtaler.Configuration.getOrNull
import no.nav.sosialhjelp.avtaler.HttpClientConfig
import no.nav.sosialhjelp.avtaler.utils.Utils.retry
import java.time.Instant
import java.util.Date
import java.util.UUID

private val logger = KotlinLogging.logger {}
private const val GRANT_TYPE_VALUE = "urn:ietf:params:oauth:grant-type:jwt-bearer"
private const val GRANT_TYPE = "grant_type"
private const val ASSERTION = "assertion"
internal const val MAX_EXPIRY_SECONDS = 120L
internal const val CLAIMS_SCOPE = "scope"

class MaskinportenService(private val maskinportenConfig: Configuration.MaskinportenProperties) {
    private val mutex = Mutex()

    @Volatile
    private var token: AccessToken = runBlocking {
        AccessToken(requestToken())
    }

    suspend fun getToken(): String {
        val expires = Instant.now().plusSeconds(120L)
        return mutex.withLock {
            if (token.expiresAt.isBefore(expires)) {
                token = AccessToken(requestToken())
            }
            token.accessToken
        }
    }

    private suspend fun requestToken(): MaskinportenAccessTokenResponse {
        logger.info("Request new maskinporten accessToken.")
        val metadata: Configuration.Metadata = Configuration.wellKnowConfig(maskinportenConfig.wellKnownUrl)
        return retry {
            val jwt = generateJWT(metadata)
            val response = HttpClientConfig.httpClient().submitForm(
                url = metadata.tokenEndpoint,
                formParameters = parametersOf(
                    GRANT_TYPE to listOf(GRANT_TYPE_VALUE),
                    ASSERTION to listOf(jwt)
                )
            )
            response.body()
        }
    }


    private fun generateJWT(metadata: Configuration.Metadata): String {
        val now = Instant.now()
        return JWT.create()
            .withSubject(maskinportenConfig.clientId)
            .withIssuer(maskinportenConfig.clientId)
            .withAudience(metadata.issuer)
            .withClaim(CLAIMS_SCOPE, maskinportenConfig.scopes)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(now.plusSeconds(MAX_EXPIRY_SECONDS)))
            .withKeyId(maskinportenConfig.clientJwk.keyID)
            .withJWTId(UUID.randomUUID().toString())
            .sign(Algorithm.RSA256(null, maskinportenConfig.clientJwk.toRSAPrivateKey()))
    }
}

data class MaskinportenAccessTokenResponse(
    @JsonAlias("access_token") val accessToken: String,
    @JsonAlias("expires_in") val expiresIn: Long,
    @JsonAlias("scope") val scope: String = "",
    @JsonAlias("audience") val audience: String = "",
    @JsonAlias("token_type") val tokenType: String = ""
)

private data class AccessToken(
    val accessToken: String,
    val expiresAt: Instant
) {
    constructor(tokenResponse: MaskinportenAccessTokenResponse) : this(
        accessToken = tokenResponse.accessToken,
        expiresAt = Instant.now().plusSeconds(tokenResponse.expiresIn)
    )
}