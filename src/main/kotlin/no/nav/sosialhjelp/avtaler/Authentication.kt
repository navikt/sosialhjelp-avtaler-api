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
import io.ktor.server.auth.jwt.JWTPrincipal
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
    var maskinportenConfig: AuthenticationConfiguration
    var tokenXConfig: AuthenticationConfiguration
    runBlocking(Dispatchers.IO) {
        tokenXConfig = AuthenticationConfiguration(
            metadata = httpClient.get(Configuration.tokenXProperties.wellKnownUrl).body(),
            clientId = Configuration.tokenXProperties.clientId,
        )
    }
    runBlocking(Dispatchers.IO) {
        maskinportenConfig = AuthenticationConfiguration(
            metadata = httpClient.get(Configuration.maskinportenProperties.wellKnownUrl).body(),
            clientId = Configuration.maskinportenProperties.clientId,
        )
    }

    val jwkProviderTokenx = JwkProviderBuilder(URL(tokenXConfig.metadata.jwksUri))
        // cache up to 1000 JWKs for 24 hours
        .cached(1000, 24, TimeUnit.HOURS)
        // if not cached, only allow max 100 different keys per minute to be fetched from external provider
        .rateLimited(100, 1, TimeUnit.MINUTES)
        .build()

    val jwkProviderMaskinporten = JwkProviderBuilder(URL(maskinportenConfig.metadata.jwksUri))
        // cache up to 10 JWKs for 24 hours
        .cached(10, 24, TimeUnit.HOURS)
        // if not cached, only allow max 10 different keys per minute to be fetched from external provider
        .rateLimited(10, 1, TimeUnit.MINUTES)
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

    // Install auth providers
    authentication {
        jwt("maskinporten") {
            verifier(jwkProviderMaskinporten, maskinportenConfig.metadata.issuer)
            validate { credentials ->
                try {
                    // If these two are not null they will have been checked by the verifier
                    requireNotNull(credentials.payload.getClaim("exp")) {
                        "Auth: Missing exp in token"
                    }
                    requireNotNull(credentials.payload.getClaim("iat")) {
                        "Auth: Missing iat in token"
                    }

                    // The remaining ones have to be not null, but we dont check their contents here
                    requireNotNull(credentials.payload.getClaim("client_amr")) {
                        "Auth: Missing client_amr in token"
                    }

                    requireNotNull(credentials.payload.getClaim("client_id")) {
                        "Auth: Missing client_id in token"
                    }

                    requireNotNull(credentials.payload.getClaim("consumer")) {
                        "Auth: Missing consumer in token"
                    }

                    requireNotNull(credentials.payload.getClaim("jti")) {
                        "Auth: Missing jti in token"
                    }

                    // We also require a scope to be set to access any of our apis
                    requireNotNull(credentials.payload.getClaim("scope")) {
                        "Auth: Missing scope in token"
                    }

                    JWTPrincipal(credentials.payload)
                } catch (e: Throwable) {
                    // log.warn("Client authentication failed with exception: $e")
                    e.printStackTrace()
                    null
                }
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
