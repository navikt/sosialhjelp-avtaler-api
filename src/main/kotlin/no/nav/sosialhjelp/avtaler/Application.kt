package no.nav.sosialhjelp.avtaler

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.nimbusds.oauth2.sdk.auth.ClientAuthenticationMethod
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import mu.KotlinLogging
import no.nav.security.token.support.client.core.ClientAuthenticationProperties
import no.nav.sosialhjelp.avtaler.HttpClientConfig.httpClient
import no.nav.sosialhjelp.avtaler.altinn.AltinnClient
import no.nav.sosialhjelp.avtaler.altinn.AltinnService
import no.nav.sosialhjelp.avtaler.auth.Oauth2Client
import no.nav.sosialhjelp.avtaler.avtaler.AvtaleService
import no.nav.sosialhjelp.avtaler.avtaler.avtaleApi
import no.nav.sosialhjelp.avtaler.db.DefaultDatabaseContext
import no.nav.sosialhjelp.avtaler.digipost.DigipostClient
import no.nav.sosialhjelp.avtaler.digipost.DigipostService
import no.nav.sosialhjelp.avtaler.enhetsregisteret.EnhetsregisteretClient
import no.nav.sosialhjelp.avtaler.enhetsregisteret.EnhetsregisteretService
import no.nav.sosialhjelp.avtaler.internal.internalRoutes
import no.nav.sosialhjelp.avtaler.kommune.kommuneApi
import no.nav.sosialhjelp.avtaler.pdl.PdlClient
import no.nav.sosialhjelp.avtaler.pdl.PersonNavnService
import java.util.TimeZone

private val log = KotlinLogging.logger {}

fun main(args: Array<String>) {
    io.ktor.server.cio.EngineMain.main(args)
}

fun Application.module() {
    val host = environment.config.propertyOrNull("ktor.deployment.host")?.getString() ?: "0.0.0.0"
    val port = environment.config.propertyOrNull("ktor.deployment.port")?.getString() ?: "8080"

    log.info("sosialhjelp-avtaler-api starting up on $host:$port...")
    configure()
    setupRoutes()
}

fun Application.configure() {
    TimeZone.setDefault(TimeZone.getTimeZone("Europe/Oslo"))
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }
    install(IgnoreTrailingSlash)
}

fun Application.setupRoutes() {
    val databaseContext = DefaultDatabaseContext(DatabaseConfiguration(Configuration.dbProperties, Configuration.profile).dataSource())

    installAuthentication(httpClient(engineFactory { StubEngine.tokenX() }))

    // Token X
    val authProperties = ClientAuthenticationProperties.builder()
        .clientId(Configuration.tokenXProperties.clientId)
        .clientJwk(Configuration.tokenXProperties.privateJwk)
        .clientAuthMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
        .build()

    val defaultHttpClient = defaultHttpClient()

    val tokenExchangeClient = Oauth2Client(defaultHttpClient, authProperties, Configuration.tokenXProperties)
    val altinnService = AltinnService(AltinnClient(Configuration.altinnProperties, tokenExchangeClient))
    val digipostService = DigipostService(DigipostClient(Configuration.digipostProperties, Configuration.virksomhetssertifikatProperties))
    val enhetsregisteretService = EnhetsregisteretService(EnhetsregisteretClient(Configuration.enhetsregistertetProperties, defaultHttpClient()))
    val avtaleService = AvtaleService(altinnService, digipostService, enhetsregisteretService, databaseContext)
    val personNavnService = PersonNavnService(PdlClient(Configuration.pdlProperties, tokenExchangeClient))

    routing {

        route("/sosialhjelp/avtaler-api") {
            internalRoutes()
            route("/api") {
                authenticate(if (Configuration.local) "local" else TOKEN_X_AUTH) {
                    avtaleApi(avtaleService, personNavnService)
                    kommuneApi(avtaleService)
                }
            }
        }
    }
}
