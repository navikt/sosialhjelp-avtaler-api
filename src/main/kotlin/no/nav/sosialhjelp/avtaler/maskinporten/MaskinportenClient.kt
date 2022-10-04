package no.nav.sosialhjelp.avtaler.maskinporten

import com.nimbusds.jose.Algorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.oauth2.sdk.JWTBearerGrant
import com.nimbusds.oauth2.sdk.Scope
import com.nimbusds.oauth2.sdk.TokenRequest
import com.nimbusds.oauth2.sdk.TokenResponse
import no.nav.sosialhjelp.avtaler.Configuration
import no.nav.sosialhjelp.avtaler.Configuration.getOrNull
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Instant
import java.util.*

interface MaskinportenClient {

    fun hentAltinnToken(): String
}

class MaskinportenClientImpl(
    private val props: Configuration.MaskinportenProperties
) : MaskinportenClient {

    private val log = LoggerFactory.getLogger(javaClass)

    private val tokenEndpoint: URI

    private val privateJwkKeyId: String

    private val assertionSigner: JWSSigner

    private val scopesList = props.scopes.split(" ")

    private fun getRSAkey(clientJwk: String?): RSAKey = clientJwk?.let { RSAKey.parse(it) } ?: generateRSAKey()

    private fun generateRSAKey() = RSAKeyGenerator(2048)
        .keyID(UUID.randomUUID().toString())
        .keyUse(KeyUse.SIGNATURE)
        .algorithm(Algorithm.parse("RS256"))
        .generate()

    init {
        val rsaKey: RSAKey = getRSAkey(getOrNull(props.privateJwk))

        tokenEndpoint = URI.create(props.tokenEndpointUrl)
        privateJwkKeyId = rsaKey.keyID
        assertionSigner = RSASSASigner(rsaKey)
    }

    override fun hentAltinnToken(): String {
        val signedJwt = signedClientAssertion(
            clientAssertionHeader(privateJwkKeyId),
            clientAssertionClaims(
                props.clientId,
                props.issuer,
                props.altinnUrl,
                scopesList
            ),
            assertionSigner
        )

        val request = TokenRequest(
            tokenEndpoint,
            JWTBearerGrant(signedJwt),
            Scope(*scopesList.toTypedArray()),
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
