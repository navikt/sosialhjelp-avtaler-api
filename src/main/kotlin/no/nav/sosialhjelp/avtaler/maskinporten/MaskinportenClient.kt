package no.nav.sosialhjelp.avtaler.maskinporten

import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.oauth2.sdk.JWTBearerGrant
import com.nimbusds.oauth2.sdk.Scope
import com.nimbusds.oauth2.sdk.TokenRequest
import com.nimbusds.oauth2.sdk.TokenResponse
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Instant
import java.util.*

interface MaskinportenClient {

    fun hentAltinnToken(): String
}
class MaskinportenClientImpl(
    private val clientId: String,
    private val issuer: String,
    private val altinnUrl: String,
    private val scopes: List<String>,
    tokenEndpointUrl: String,
    privateJwk: String,
) : MaskinportenClient {

    private val log = LoggerFactory.getLogger(javaClass)

    private val tokenEndpoint: URI

    private val privateJwkKeyId: String

    private val assertionSigner: JWSSigner

    init {
        val rsaKey = RSAKey.parse(privateJwk)

        tokenEndpoint = URI.create(tokenEndpointUrl)
        privateJwkKeyId = rsaKey.keyID
        assertionSigner = RSASSASigner(rsaKey)
    }

    override fun hentAltinnToken(): String {
        val signedJwt = signedClientAssertion(
            clientAssertionHeader(privateJwkKeyId),
            clientAssertionClaims(
                clientId,
                issuer,
                altinnUrl,
                scopes
            ),
            assertionSigner
        )

        val request = TokenRequest(
            tokenEndpoint,
            JWTBearerGrant(signedJwt),
            Scope(*scopes.toTypedArray()),
        )

        val response = TokenResponse.parse(request.toHTTPRequest().send())

        if (!response.indicatesSuccess()) {
            val tokenErrorResponse = response.toErrorResponse()

            log.error("Failed to fetch maskinporten token. Error: {}", tokenErrorResponse.toJSONObject().toString())

            throw RuntimeException("Failed to fetch maskinporten token")
        }

        val successResponse = response.toSuccessResponse()

        val accessToken = successResponse.tokens.accessToken

        return accessToken.value
    }

    private fun signedClientAssertion(
        assertionHeader: JWSHeader,
        assertionClaims: JWTClaimsSet,
        signer: JWSSigner
    ): SignedJWT {
        val signedJWT = SignedJWT(assertionHeader, assertionClaims)
        signedJWT.sign(signer)
        return signedJWT
    }

    private fun clientAssertionHeader(keyId: String): JWSHeader {
        val headerClaims: MutableMap<String, Any> = HashMap()
        headerClaims["kid"] = keyId
        headerClaims["typ"] = "JWT"
        headerClaims["alg"] = "RS256"
        return JWSHeader.parse(headerClaims)
    }

    private fun clientAssertionClaims(
        clientId: String,
        issuer: String,
        altinnUrl: String,
        scopes: List<String>
    ): JWTClaimsSet {
        val now = Instant.now()
        val expire = now.plusSeconds(30)

        return JWTClaimsSet.Builder()
            .subject(clientId)
            .audience(issuer)
            .issuer(clientId)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(expire))
            .notBeforeTime(Date.from(now))
            .claim("scope", scopes.joinToString(" "))
            .claim("resource", altinnUrl)
            .jwtID(UUID.randomUUID().toString())
            .build()
    }
}
