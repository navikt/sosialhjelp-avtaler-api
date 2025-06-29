package no.nav.sosialhjelp.avtaler.auth

import com.nimbusds.oauth2.sdk.GrantType
import com.nimbusds.oauth2.sdk.auth.JWTAuthentication
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import mu.KotlinLogging
import no.nav.security.token.support.client.core.ClientAuthenticationProperties
import no.nav.security.token.support.client.core.OAuth2ParameterNames
import no.nav.security.token.support.client.core.auth.ClientAssertion
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenResponse
import no.nav.sosialhjelp.avtaler.Configuration
import java.net.URI

private val log = KotlinLogging.logger { }

interface Oauth2Client {
    suspend fun exchangeToken(
        token: String,
        audience: String,
    ): OAuth2AccessTokenResponse
}

class Oauth2ClientLocal : Oauth2Client {
    override suspend fun exchangeToken(
        token: String,
        audience: String,
    ): OAuth2AccessTokenResponse = OAuth2AccessTokenResponse("abc", 1, 1, emptyMap())
}

class Oauth2ClientImpl(
    private val httpClient: HttpClient,
    private val clientAuthProperties: ClientAuthenticationProperties,
    tokenXProperties: Configuration.TokenXProperties,
) : Oauth2Client {
    private val tokenXTokenEndpointUrl = tokenXProperties.tokenXTokenEndpoint

    override suspend fun exchangeToken(
        token: String,
        audience: String,
    ): OAuth2AccessTokenResponse {
        log.info { "exhangeToken tokenendpointurl:$tokenXTokenEndpointUrl,  audience:$audience " }
        val grant = GrantRequest.tokenExchange(token, audience)
        return httpClient.tokenRequest(
            tokenXTokenEndpointUrl,
            clientAuthProperties = clientAuthProperties,
            grantRequest = grant,
        )
    }
}

data class GrantRequest(
    val grantType: GrantType,
    val params: Map<String, String> = emptyMap(),
) {
    companion object {
        fun tokenExchange(
            token: String,
            audience: String,
        ): GrantRequest =
            GrantRequest(
                grantType = GrantType.TOKEN_EXCHANGE,
                params =
                    mapOf(
                        OAuth2ParameterNames.SUBJECT_TOKEN_TYPE to "urn:ietf:params:oauth:token-type:jwt",
                        OAuth2ParameterNames.SUBJECT_TOKEN to token,
                        OAuth2ParameterNames.AUDIENCE to audience,
                    ),
            )
    }
}

internal suspend fun HttpClient.tokenRequest(
    tokenEndpointUrl: String,
    clientAuthProperties: ClientAuthenticationProperties,
    grantRequest: GrantRequest,
): OAuth2AccessTokenResponse =
    submitForm(
        url = tokenEndpointUrl,
        formParameters =
            Parameters.build {
                appendClientAuthParams(
                    tokenEndpointUrl = tokenEndpointUrl,
                    clientAuthProperties = clientAuthProperties,
                )
                append(OAuth2ParameterNames.GRANT_TYPE, grantRequest.grantType.value)
                grantRequest.params.forEach {
                    append(it.key, it.value)
                }
            },
    ).body()

private fun ParametersBuilder.appendClientAuthParams(
    tokenEndpointUrl: String,
    clientAuthProperties: ClientAuthenticationProperties,
) = apply {
    val clientAssertion = ClientAssertion(URI.create(tokenEndpointUrl), clientAuthProperties)
    append(OAuth2ParameterNames.CLIENT_ID, clientAuthProperties.clientId)
    append(OAuth2ParameterNames.CLIENT_ASSERTION_TYPE, JWTAuthentication.CLIENT_ASSERTION_TYPE)
    append(OAuth2ParameterNames.CLIENT_ASSERTION, clientAssertion.assertion())
}
