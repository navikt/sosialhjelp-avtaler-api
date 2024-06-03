package no.nav.sosialhjelp.avtaler

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.interfaces.Payload
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
import java.net.URI
import java.util.concurrent.TimeUnit

const val TOKEN_X_AUTH = "tokenX"
const val AZURE_AUTH = "azure"

private val log = KotlinLogging.logger {}

fun Application.installAuthentication(httpClient: HttpClient) {
    var tokenXConfig: AuthenticationConfiguration
    var azureConfig: AuthenticationConfiguration
    runBlocking(Dispatchers.IO) {
        azureConfig =
            AuthenticationConfiguration(
                metadata = httpClient.get(Configuration.azureProperties.wellKnownUrl).body(),
                clientId = Configuration.azureProperties.clientId,
            )
        tokenXConfig =
            AuthenticationConfiguration(
                metadata = httpClient.get(Configuration.tokenXProperties.wellKnownUrl).body(),
                clientId = Configuration.tokenXProperties.clientId,
            )
    }

    val jwkProviderTokenx =
        JwkProviderBuilder(URI.create(tokenXConfig.metadata.jwksUri).toURL())
            // cache up to 1000 JWKs for 24 hours
            .cached(1000, 24, TimeUnit.HOURS)
            // if not cached, only allow max 100 different keys per minute to be fetched from external provider
            .rateLimited(100, 1, TimeUnit.MINUTES)
            .build()

    val abcProviderTokenAzure =
        JwkProviderBuilder(URI.create(azureConfig.metadata.jwksUri).toURL()).cached(1000, 24, TimeUnit.HOURS)
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
        jwt(AZURE_AUTH) {
            verifier(abcProviderTokenAzure, azureConfig.metadata.issuer)
            validate { credentials ->
                if (isValidAzureToken(credentials.payload, jwtIssuer = azureConfig.metadata.issuer, clientId = azureConfig.clientId)) {
                    val email = credentials.payload.getClaim("preferred_username").asString()
                    requireNotNull(email)
                    AzureUserPrincipal(email)
                } else {
                    log.warn("Something is wrong here with issuer")
                    throw IllegalArgumentException("Auth: Invalid token")
                }
            }
        }
        provider("local") {
            authenticate { context ->
                context.principal(UserPrincipal("15084300133"))
            }
        }
    }
}

fun isValidAzureToken(
    payload: Payload,
    jwtIssuer: String,
    clientId: String,
): Boolean {
    if (payload.issuer != jwtIssuer) {
//        logger.warn("Something is wrong here with issuer")
        return false
    }
    if (!payload.audience.contains(clientId)) {
//        logger.warn("Something is wrong here with audience")
        return false
    }
    return true
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

internal data class UserPrincipal(val fnr: String) : Principal

internal data class AzureUserPrincipal(val email: String) : Principal

fun ApplicationCall.extractFnr(): String {
    val fnrFromClaims = this.principal<UserPrincipal>()?.fnr
    if (fnrFromClaims == null || fnrFromClaims.trim().isEmpty()) {
        throw RuntimeException("Fant ikke FNR i token")
    }
    return fnrFromClaims
}

fun ApplicationCall.extractEmail(): String {
    val emailFromClaims = this.principal<AzureUserPrincipal>()?.email
    if (emailFromClaims == null || emailFromClaims.trim().isEmpty()) {
        throw RuntimeException("Fant ikke email i azure-token")
    }
    return emailFromClaims
}
