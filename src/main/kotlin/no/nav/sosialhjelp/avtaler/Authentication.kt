package no.nav.sosialhjelp.avtaler

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.Principal
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.net.URL
import java.util.concurrent.TimeUnit

const val TOKEN_X_AUTH = "tokenX"

private val log = KotlinLogging.logger {}

fun Application.installAuthentication(httpClient: HttpClient) {
    var tokenXConfig: AuthenticationConfiguration
    runBlocking(Dispatchers.IO) {
        tokenXConfig = AuthenticationConfiguration(
            metadata = httpClient.get(Configuration.tokenXProperties.wellKnownUrl).body(),
            clientId = Configuration.tokenXProperties.clientId,
        )
    }

    val jwkProviderTokenx = JwkProviderBuilder(URL(tokenXConfig.metadata.jwksUri))
        // cache up to 1000 JWKs for 24 hours
        .cached(1000, 24, TimeUnit.HOURS)
        // if not cached, only allow max 100 different keys per minute to be fetched from external provider
        .rateLimited(100, 1, TimeUnit.MINUTES)
        .build()

    authentication {
        jwt(TOKEN_X_AUTH) {
            verifier(jwkProviderTokenx, tokenXConfig.metadata.issuer)
            validate { credentials ->
                requireNotNull(credentials.payload.audience) {
                    "Auth: Missing audience in token"
                }
                require(credentials.payload.audience.contains(tokenXConfig.clientId)) {
                    "Auth: Valid audience not found in claims"
                }
                require(credentials.payload.getClaim("acr").asString() == ("Level4")) { "Auth: Level4 required" }
                UserPrincipal(credentials.payload.getClaim(Configuration.tokenXProperties.userclaim).asString())
            }
        }
        provider("local") {
            authenticate { context ->
                context.principal(UserPrincipal("15084300133"))
            }
        }
    }
}

private data class AuthenticationConfiguration(
    val metadata: Metadata,
    val clientId: String,
) {
    data class Metadata(
        @JsonProperty("issuer") val issuer: String,
        @JsonProperty("jwks_uri") val jwksUri: String,
    )
}

internal class UserPrincipal(private val fnr: String) : Principal {
    fun getFnr() = fnr
}

fun ApplicationCall.extractFnr(): String {
    val fnrFromClaims = this.principal<UserPrincipal>()?.getFnr()
    if (fnrFromClaims == null || fnrFromClaims.trim().isEmpty()) {
        throw RuntimeException("Fant ikke FNR i token")
    }
    return fnrFromClaims
}
